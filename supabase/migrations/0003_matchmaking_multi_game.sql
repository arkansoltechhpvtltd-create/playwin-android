-- Makes random matchmaking game-agnostic, so games other than Connect 4
-- (starting with Ludo) can use the same queue and `rooms` rows.
-- Run this whole file once in the Supabase SQL editor, AFTER 0002_matchmaking.sql.
--
-- Changes from 0002:
--  - A new room's board_state is only pre-filled for Connect 4. Every other
--    game starts with '{}', which both clients read as "the game's standard
--    starting position" - so the SQL never needs to know another game's rules.
--  - p_game_id is validated, so a client can't create rooms for junk games.
--  - A queue entry paired into a room for a *different* game (e.g. the app
--    died after being paired for Connect 4, then the player searched Ludo)
--    is never handed back for the wrong game; that stale room is abandoned.
--  - One advisory lock covers all games, since a player's single queue row
--    can move between games.

create or replace function public.matchmake(p_game_id text default 'CONNECT_FOUR')
returns setof public.rooms
language plpgsql
security definer
set search_path = public
as $$
declare
  v_me uuid := auth.uid();
  v_mine public.matchmaking_queue;
  v_opponent uuid;
  v_room public.rooms;
begin
  if v_me is null then
    raise exception 'Not authenticated';
  end if;
  if p_game_id is null or p_game_id !~ '^[A-Z_]{1,32}$' then
    raise exception 'Invalid game';
  end if;

  perform pg_advisory_xact_lock(hashtext('playwin_matchmaking'));

  select * into v_mine from public.matchmaking_queue where user_id = v_me;

  -- a) Already paired by someone else.
  if found and v_mine.room_id is not null then
    delete from public.matchmaking_queue where user_id = v_me;
    select * into v_room from public.rooms where id = v_mine.room_id;
    if v_room.status = 'active' and v_room.game_id = p_game_id then
      return next v_room;
      return;
    end if;
    if v_room.status = 'active' then
      update public.rooms set status = 'abandoned' where id = v_room.id;
    end if;
  end if;

  -- b) Pair with the longest-waiting live player for this game.
  select user_id into v_opponent
  from public.matchmaking_queue
  where game_id = p_game_id
    and room_id is null
    and user_id <> v_me
    and last_seen > now() - interval '10 seconds'
  order by created_at
  limit 1;

  if v_opponent is not null then
    insert into public.rooms (game_id, host_id, guest_id, status, current_turn, board_state)
    values (
      p_game_id,
      v_opponent,
      v_me,
      'active',
      v_opponent,
      case p_game_id
        when 'CONNECT_FOUR' then
          '[[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0]]'::jsonb
        else '{}'::jsonb
      end
    )
    returning * into v_room;

    update public.matchmaking_queue set room_id = v_room.id where user_id = v_opponent;
    delete from public.matchmaking_queue where user_id = v_me;
    return next v_room;
    return;
  end if;

  -- c) Nobody to pair with yet - wait in the queue.
  insert into public.matchmaking_queue (user_id, game_id)
  values (v_me, p_game_id)
  on conflict (user_id) do update
    set last_seen = now(),
        room_id = null,
        -- switching games (or coming back after going stale) restarts your place in line
        created_at = case
          when public.matchmaking_queue.game_id = excluded.game_id
           and public.matchmaking_queue.last_seen > now() - interval '10 seconds'
          then public.matchmaking_queue.created_at
          else now()
        end,
        game_id = excluded.game_id;

  return;
end;
$$;

create or replace function public.cancel_matchmaking(p_game_id text default 'CONNECT_FOUR')
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_room_id uuid;
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  perform pg_advisory_xact_lock(hashtext('playwin_matchmaking'));

  delete from public.matchmaking_queue
  where user_id = auth.uid()
  returning room_id into v_room_id;

  if v_room_id is not null then
    update public.rooms set status = 'abandoned'
    where id = v_room_id and status = 'active';
  end if;
end;
$$;
