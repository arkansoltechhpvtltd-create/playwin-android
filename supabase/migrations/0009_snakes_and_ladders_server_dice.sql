-- Snakes & Ladders: the server throws the die.
-- Run this whole file once in the Supabase SQL editor, AFTER 0008.
--
-- Until now the roller's app threw the die and wrote the resulting board, so
-- a modified app could write any roll it liked. Now:
--  1. roll_and_move(room) is the only way to take a Snakes & Ladders turn. It
--     checks it's the caller's turn, throws the die from Postgres's strong
--     random source, applies the rules (walk, ladder or snake, 6 = roll again,
--     three 6s in a row forfeit, reaching or passing 100 wins) and writes the
--     new board, whose turn is next, and the winner - all in one update.
--  2. rooms_guard refuses any other write that changes a Snakes & Ladders
--     board or turn, so the app can't go round the function. Leaving or
--     abandoning (a status-only write) still works as before.
--
-- The rules here mirror SnakesAndLaddersRules / SnakesAndLaddersBoard in the
-- app, which still uses them for vs-Computer play and to replay an online
-- roll on screen. A unit test (SnakesAndLaddersServerRulesTest) reads this
-- file and fails if the layout or the sixes limit drift apart - change both
-- together.
--
-- The board_state written is the shape the app reads:
--   {"positions": [host, guest], "turn": 0|1, "sixes": n, "turns": n,
--    "last": {"player": 0|1, "roll": 1..6, "from": n, "forfeited": bool}}

begin;

-- 1. The guard: everything from 0004, plus Snakes & Ladders moves only via roll_and_move().
create or replace function public.rooms_guard()
returns trigger
language plpgsql
as $$
begin
  if new.id <> old.id
     or new.code is distinct from old.code
     or new.host_id <> old.host_id
     or new.guest_id is distinct from old.guest_id then
    raise exception 'Cannot change room identity fields';
  end if;

  if new.board_state is distinct from old.board_state
     or new.current_turn is distinct from old.current_turn then
    if old.status <> 'active' then
      raise exception 'Room is not active';
    end if;
    if old.current_turn is null or auth.uid() is distinct from old.current_turn then
      raise exception 'Not your turn';
    end if;
    -- Set (transaction-locally) only inside roll_and_move().
    if old.game_id = 'SNAKES_AND_LADDERS'
       and coalesce(current_setting('playwin.server_move', true), '') <> 'on' then
      raise exception 'Snakes & Ladders turns are taken with roll_and_move()';
    end if;
  end if;

  if new.board_state is not distinct from old.board_state
     and new.current_turn is not distinct from old.current_turn then
    if new.winner_id is distinct from old.winner_id then
      raise exception 'winner_id can only change together with a move';
    end if;
    if new.status = 'finished' and old.status <> 'finished' then
      raise exception 'status can only become finished together with a move';
    end if;
  end if;

  new.updated_at = now();
  return new;
end;
$$;

-- 2. The roll.
create or replace function public.roll_and_move(p_room_id uuid)
returns public.rooms
language plpgsql
security definer
set search_path = public
as $$
declare
  c_finish constant int := 100;
  -- Sixes in a row that forfeit the turn (SnakesAndLaddersRules.MAX_SIXES_IN_A_ROW).
  c_max_sixes constant int := 3;
  -- Ladder bottom -> top and snake head -> tail (SnakesAndLaddersBoard.LADDERS / SNAKES).
  c_ladders constant jsonb := '{"4": 14, "9": 31, "20": 38, "28": 84, "36": 44, "40": 59, "51": 67, "63": 81, "71": 91}';
  c_snakes constant jsonb := '{"17": 7, "47": 26, "54": 34, "62": 19, "64": 60, "87": 24, "93": 73, "95": 75, "99": 78}';
  v_me uuid := auth.uid();
  v_room public.rooms;
  v_state jsonb;
  v_positions int[];
  v_turn int;
  v_sixes int;
  v_turns int;
  v_player int;
  v_from int;
  v_roll int;
  v_landed int;
  v_to int;
  v_forfeited boolean;
  v_won boolean;
  v_extra boolean;
  v_next int;
begin
  if v_me is null then
    raise exception 'Not authenticated';
  end if;

  select * into v_room from public.rooms where id = p_room_id for update;
  if not found or (v_me <> v_room.host_id and v_me is distinct from v_room.guest_id) then
    raise exception 'Room not found';
  end if;
  if v_room.game_id <> 'SNAKES_AND_LADDERS' then
    raise exception 'This game has no server-side roll';
  end if;
  if v_room.status <> 'active' then
    raise exception 'Room is not active';
  end if;
  if v_room.current_turn is distinct from v_me then
    raise exception 'Not your turn';
  end if;

  -- A fresh room holds '{}': both players off the board, the host to roll.
  v_state := v_room.board_state;
  if jsonb_typeof(v_state -> 'positions') = 'array' then
    select array_agg(value::int order by ordinality) into v_positions
    from jsonb_array_elements_text(v_state -> 'positions') with ordinality;
  else
    v_positions := array[0, 0];
  end if;
  v_turn := coalesce((v_state ->> 'turn')::int, 0);
  v_sixes := coalesce((v_state ->> 'sixes')::int, 0);
  v_turns := coalesce((v_state ->> 'turns')::int, 0);

  -- The host is player 0 and the guest player 1.
  v_player := case when v_me = v_room.host_id then 0 else 1 end;
  if v_turn <> v_player or array_length(v_positions, 1) <> 2 then
    raise exception 'Board is out of step with the room';
  end if;
  v_from := v_positions[v_player + 1];

  -- 28 bits of gen_random_uuid()'s strong randomness (always a positive int),
  -- folded onto 1..6; the leftover bias is about one in 70 million.
  v_roll := ('x' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 7))::bit(28)::int % 6 + 1;

  v_sixes := case when v_roll = 6 then v_sixes + 1 else 0 end;
  v_forfeited := v_sixes >= c_max_sixes;
  if v_forfeited then
    v_landed := v_from;
    v_to := v_from;
  else
    -- Reaching or passing 100 stops on 100: no exact roll, no bounce back.
    v_landed := least(v_from + v_roll, c_finish);
    v_to := coalesce((c_ladders ->> v_landed::text)::int, (c_snakes ->> v_landed::text)::int, v_landed);
  end if;
  v_won := v_to >= c_finish;
  v_extra := v_roll = 6 and not v_forfeited and not v_won;
  v_next := case when v_extra then v_player else 1 - v_player end;
  v_positions[v_player + 1] := v_to;

  perform set_config('playwin.server_move', 'on', true);
  update public.rooms
  set board_state = jsonb_build_object(
        'positions', to_jsonb(v_positions),
        'turn', v_next,
        'sixes', case when v_extra then v_sixes else 0 end,
        'turns', v_turns + 1,
        'last', jsonb_build_object('player', v_player, 'roll', v_roll, 'from', v_from, 'forfeited', v_forfeited)
      ),
      current_turn = case when v_won then null when v_next = 0 then host_id else guest_id end,
      status = case when v_won then 'finished' else 'active' end,
      winner_id = case when v_won then v_me else null end
  where id = p_room_id
  returning * into v_room;
  perform set_config('playwin.server_move', 'off', true);

  return v_room;
end;
$$;

revoke all on function public.roll_and_move(uuid) from public, anon;
grant execute on function public.roll_and_move(uuid) to authenticated;

commit;
