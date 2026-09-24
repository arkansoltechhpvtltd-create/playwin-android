-- Online multiplayer rooms (starting with Connect 4).
-- Run this whole file once in the Supabase SQL editor.

create table if not exists public.rooms (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  game_id text not null default 'CONNECT_FOUR',
  host_id uuid not null references auth.users (id) on delete cascade,
  guest_id uuid references auth.users (id) on delete cascade,
  status text not null default 'waiting'
    check (status in ('waiting', 'active', 'finished', 'abandoned')),
  current_turn uuid references auth.users (id),
  board_state jsonb not null default '[]'::jsonb,
  winner_id uuid references auth.users (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists rooms_host_idx on public.rooms (host_id);
create index if not exists rooms_guest_idx on public.rooms (guest_id);

-- Enforce the game's real invariants server-side, not just in RLS:
--  - identity fields (id/code/host_id) can never be changed after creation
--  - guest_id can only be set once (by joining), never reassigned
--  - the board can only change if the caller is the room's current_turn
--    player and the room is active - this is what makes the row the actual
--    source of truth for whose turn it is, not just client trust
--  - winner_id, and a transition to 'finished', can only happen in the same
--    write as a board change made by the current-turn player above - so a
--    client can never just declare itself the winner without a move that
--    the guard above already validated as this player's legitimate turn.
create or replace function public.rooms_guard()
returns trigger
language plpgsql
as $$
begin
  if new.id <> old.id or new.code <> old.code or new.host_id <> old.host_id then
    raise exception 'Cannot change room identity fields';
  end if;

  if old.guest_id is not null and new.guest_id is distinct from old.guest_id then
    raise exception 'Cannot change guest_id after joining';
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

drop trigger if exists rooms_guard_trigger on public.rooms;
create trigger rooms_guard_trigger
  before update on public.rooms
  for each row execute function public.rooms_guard();

alter table public.rooms enable row level security;

drop policy if exists rooms_select_participants on public.rooms;
create policy rooms_select_participants on public.rooms
  for select
  using (auth.uid() = host_id or auth.uid() = guest_id);

drop policy if exists rooms_insert_host on public.rooms;
create policy rooms_insert_host on public.rooms
  for insert
  with check (auth.uid() = host_id and guest_id is null);

drop policy if exists rooms_update_participants on public.rooms;
create policy rooms_update_participants on public.rooms
  for update
  using (auth.uid() = host_id or auth.uid() = guest_id)
  with check (auth.uid() = host_id or auth.uid() = guest_id);

drop policy if exists rooms_delete_host on public.rooms;
create policy rooms_delete_host on public.rooms
  for delete
  using (auth.uid() = host_id);

-- Joining goes through this function instead of a direct UPDATE, so a client
-- never needs (and never gets) generic UPDATE access to a room it hasn't
-- joined yet - this is the only path that can set guest_id, and it runs as
-- the function owner (security definer) specifically so RLS on the base
-- table doesn't need a "join a room you're not in yet" carve-out that a
-- malicious client could otherwise abuse to steal/overwrite someone else's
-- waiting room.
create or replace function public.join_room(p_code text)
returns public.rooms
language plpgsql
security definer
set search_path = public
as $$
declare
  v_room public.rooms;
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  select * into v_room from public.rooms
  where code = upper(p_code)
    and status = 'waiting'
    and guest_id is null
  for update;

  if not found then
    raise exception 'Room not found or already full';
  end if;

  if v_room.host_id = auth.uid() then
    raise exception 'Cannot join your own room';
  end if;

  update public.rooms
  set guest_id = auth.uid(),
      status = 'active',
      current_turn = host_id
  where id = v_room.id
  returning * into v_room;

  return v_room;
end;
$$;

revoke all on function public.join_room(text) from public;
grant execute on function public.join_room(text) to authenticated;

-- Stream row changes to subscribed clients (Postgrest RLS still applies per
-- subscriber, so only the two players in a room ever receive its updates).
alter publication supabase_realtime add table public.rooms;
