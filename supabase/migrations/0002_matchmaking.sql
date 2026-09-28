-- Random matchmaking for online rooms (starting with Connect 4).
-- Run this whole file once in the Supabase SQL editor, AFTER 0001_rooms.sql.
--
-- Replaces the "create room / join with code" flow: two players can no longer
-- choose each other. A player can only ever get into a room by being paired
-- by the server from the matchmaking queue below, so a room code is no longer
-- a thing - and the old code-based entry points (join_room RPC, client-side
-- room INSERT) are removed so a modified client can't bring them back.

-- 1. Remove the code-sharing paths --------------------------------------------

drop function if exists public.join_room(text);

-- Clients may no longer insert rooms at all; only matchmake() (security
-- definer, below) creates them, always with both players already set.
drop policy if exists rooms_insert_host on public.rooms;

-- Rooms no longer have a shareable code.
alter table public.rooms alter column code drop not null;

-- Any code-based room still waiting for a guest is dead now.
update public.rooms set status = 'abandoned' where status = 'waiting';

-- Same invariants as 0001, but null-safe on code, and guest_id is now fully
-- immutable (matchmaking sets it at creation; nothing may change it after).
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

  if new.board_state is distinct from old.board_state then
    if old.status <> 'active' then
      raise exception 'Room is not active';
    end if;
    if old.current_turn is null or auth.uid() <> old.current_turn then
      raise exception 'Not your turn';
    end if;
  else
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

-- 2. The queue ------------------------------------------------------------------

-- One row per searching player. last_seen is a heartbeat refreshed by every
-- matchmake() call, so a player whose app died mid-search stops being
-- pairable within seconds instead of trapping the next person in a room with
-- nobody on the other side. room_id is set when someone else pairs with you.
create table if not exists public.matchmaking_queue (
  user_id uuid primary key references auth.users (id) on delete cascade,
  game_id text not null default 'CONNECT_FOUR',
  room_id uuid references public.rooms (id) on delete set null,
  created_at timestamptz not null default now(),
  last_seen timestamptz not null default now()
);

create index if not exists matchmaking_queue_waiting_idx
  on public.matchmaking_queue (game_id, created_at)
  where room_id is null;

-- Clients can see only their own queue entry and can't write it directly -
-- every change goes through matchmake() / cancel_matchmaking().
alter table public.matchmaking_queue enable row level security;

drop policy if exists matchmaking_queue_select_own on public.matchmaking_queue;
create policy matchmaking_queue_select_own on public.matchmaking_queue
  for select
  using (auth.uid() = user_id);

-- 3. Pairing ----------------------------------------------------------------------

-- Called repeatedly (every ~2s) while a player is searching. Each call:
--   a) if someone already paired with you, hands you that room and removes
--      you from the queue;
--   b) otherwise tries to pair you with the longest-waiting live player -
--      creating an active room (they're host and move first, you're guest);
--   c) otherwise (re)enqueues you, refreshing your heartbeat, and returns no rows.
-- The caller can't name an opponent - there is no parameter for it.
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

  -- Serialize all matchmaking for this game so two searchers can never both
  -- grab the same opponent (queue volume is small; this is simplest-correct).
  perform pg_advisory_xact_lock(hashtext('playwin_matchmaking:' || p_game_id));

  select * into v_mine from public.matchmaking_queue where user_id = v_me;

  -- a) Already paired by someone else.
  if found and v_mine.room_id is not null then
    delete from public.matchmaking_queue where user_id = v_me;
    select * into v_room from public.rooms where id = v_mine.room_id;
    if v_room.status = 'active' then
      return next v_room;
      return;
    end if;
    -- The room died before we picked it up (opponent cancelled) - search on.
  end if;

  -- b) Pair with the longest-waiting live player.
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
      '[[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0]]'::jsonb
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
        -- switching games restarts your place in line
        created_at = case when public.matchmaking_queue.game_id = excluded.game_id
                          then public.matchmaking_queue.created_at else now() end,
        game_id = excluded.game_id;

  return;
end;
$$;

-- Leaves the queue. If someone paired with you in the instant before you
-- cancelled, that room is marked abandoned so they aren't left waiting on a
-- player who is gone.
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

  perform pg_advisory_xact_lock(hashtext('playwin_matchmaking:' || p_game_id));

  delete from public.matchmaking_queue
  where user_id = auth.uid()
  returning room_id into v_room_id;

  if v_room_id is not null then
    update public.rooms set status = 'abandoned'
    where id = v_room_id and status = 'active';
  end if;
end;
$$;

revoke all on function public.matchmake(text) from public;
revoke all on function public.cancel_matchmaking(text) from public;
grant execute on function public.matchmake(text) to authenticated;
grant execute on function public.cancel_matchmaking(text) to authenticated;
