-- Coupons are issued only by the server.
-- Run this whole file once in the Supabase SQL editor, AFTER 0009.
--
-- Until now the app picked its own coupon and inserted it into `coupons`, so a
-- modified app could mint anything. Now:
--  1. The app has SELECT on its own coupons and nothing else. Every policy on
--     the table is replaced (0001-0009 never defined them), and INSERT, UPDATE
--     and DELETE are revoked from anon and authenticated.
--  2. claim_room_coupon(room) is the only way a coupon is created. It takes a
--     room id and nothing else: the winner is read from the room row. One
--     coupon per room is enforced by a unique constraint, so a retry hands
--     back the same coupon instead of minting a second.
--  3. Only games in coupon_verified_games pay out. A game belongs there only
--     once the server itself decides its winner. Today that's Snakes & Ladders
--     (roll_and_move). Every other online game's winner_id is still written by
--     the mover's app, so it's a client claim, not a server result. Add a game
--     to this list only in the migration that adds its server-side validation.
--  4. redeem_coupon(coupon) replaces the app's direct UPDATE.
--  5. A finished or abandoned room can't change status again. Otherwise the
--     loser could flip a finished room to abandoned (a status-only write) and
--     take the win away before the winner claims it.
--  6. The app can't INSERT a room. Only matchmake() creates them.
--
-- `coupons` was created by hand, so its column types are checked up front:
-- this file stops, changing nothing, if the table isn't the shape the app uses.

begin;

create table if not exists public.coupons (
  id text primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  partner text not null,
  discount text not null,
  code text not null,
  source_game text not null,
  status text not null default 'won',
  expires_at text,
  created_at timestamptz not null default now()
);

do $$
declare
  v_expected constant jsonb := '{
    "id": "text", "user_id": "uuid", "partner": "text", "discount": "text", "code": "text",
    "source_game": "text", "status": "text", "expires_at": "text"
  }';
  v_column text;
  v_type text;
begin
  for v_column in select jsonb_object_keys(v_expected) loop
    select data_type into v_type
    from information_schema.columns
    where table_schema = 'public' and table_name = 'coupons' and column_name = v_column;
    if v_type is distinct from v_expected ->> v_column then
      raise exception 'coupons.% is %, expected %', v_column, coalesce(v_type, 'missing'), v_expected ->> v_column;
    end if;
  end loop;
end;
$$;

-- 1. The app can read its own coupons and do nothing else.
alter table public.coupons enable row level security;

do $$
declare
  v_policy text;
begin
  for v_policy in
    select policyname from pg_policies where schemaname = 'public' and tablename = 'coupons'
  loop
    execute format('drop policy %I on public.coupons', v_policy);
  end loop;
end;
$$;

create policy coupons_select_own on public.coupons
  for select
  to authenticated
  using (auth.uid() = user_id);

revoke all on public.coupons from public, anon, authenticated;
grant select on public.coupons to authenticated;

-- 2. One coupon per room. Null for any coupon from before this file.
alter table public.coupons
  add column if not exists room_id uuid references public.rooms (id) on delete set null;

create unique index if not exists coupons_room_id_key on public.coupons (room_id);

-- 3. Games whose winner the server decides itself. No client can read or write this.
create table if not exists public.coupon_verified_games (
  game_id text primary key
);

alter table public.coupon_verified_games enable row level security;

revoke all on public.coupon_verified_games from public, anon, authenticated;

insert into public.coupon_verified_games (game_id)
values ('SNAKES_AND_LADDERS')
on conflict (game_id) do nothing;

-- Mints the caller's coupon for a room they won, or returns the one already
-- minted. Returns no row for a win in a game that doesn't pay out (see above).
create or replace function public.claim_room_coupon(p_room_id uuid)
returns setof public.coupons
language plpgsql
security definer
set search_path = public
as $$
declare
  -- Partner, discount, code prefix.
  c_partners constant jsonb := '[
    ["Brew & Bean Cafe", "30% off", "BREW"],
    ["StreetStyle Fashion", "₹200 off", "STYLE"],
    ["PizzaPlace", "Buy 1 get 1", "PIZZA"],
    ["CineMax", "20% off tickets", "CINE"],
    ["FitZone Gym", "1 free session", "FIT"],
    ["QuickMart", "15% off groceries", "MART"]
  ]';
  v_me uuid := auth.uid();
  v_room public.rooms;
  v_game_name text;
  v_partner jsonb;
begin
  if v_me is null then
    raise exception 'Not authenticated';
  end if;

  -- Locked so a claim can't overlap a write to this room.
  select * into v_room from public.rooms where id = p_room_id for update;
  if not found or (v_me <> v_room.host_id and v_me is distinct from v_room.guest_id) then
    raise exception 'Room not found';
  end if;
  if v_room.status <> 'finished' or v_room.winner_id is distinct from v_me then
    raise exception 'No win to claim';
  end if;
  if not exists (select 1 from public.coupon_verified_games where game_id = v_room.game_id) then
    return;
  end if;

  select name into v_game_name from public.games where id = v_room.game_id;

  -- 28 bits of gen_random_uuid()'s strong randomness pick the partner.
  v_partner := c_partners -> (
    ('x' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 7))::bit(28)::int % jsonb_array_length(c_partners)
  );

  insert into public.coupons (id, user_id, room_id, partner, discount, code, source_game, status, expires_at)
  values (
    gen_random_uuid()::text,
    v_me,
    v_room.id,
    v_partner ->> 0,
    v_partner ->> 1,
    (v_partner ->> 2) || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 10)),
    'Won in ' || coalesce(v_game_name, v_room.game_id),
    'won',
    'Expires in 30 days'
  )
  on conflict (room_id) do nothing;

  return query select * from public.coupons where room_id = v_room.id;
end;
$$;

revoke all on function public.claim_room_coupon(uuid) from public, anon;
grant execute on function public.claim_room_coupon(uuid) to authenticated;

-- 4. Redeeming: only the owner, and only a coupon that's still won.
create or replace function public.redeem_coupon(p_coupon_id text)
returns public.coupons
language plpgsql
security definer
set search_path = public
as $$
declare
  v_me uuid := auth.uid();
  v_coupon public.coupons;
begin
  if v_me is null then
    raise exception 'Not authenticated';
  end if;

  update public.coupons
  set status = 'redeemed'
  where id = p_coupon_id and user_id = v_me and status = 'won'
  returning * into v_coupon;

  if not found then
    raise exception 'Coupon not found or already redeemed';
  end if;

  return v_coupon;
end;
$$;

revoke all on function public.redeem_coupon(text) from public, anon;
grant execute on function public.redeem_coupon(text) to authenticated;

-- 5. The guard: everything from 0009, plus finished and abandoned are final.
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

  if old.status in ('finished', 'abandoned') and new.status <> old.status then
    raise exception 'Room is over';
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

-- 6. Rooms are only created by matchmake(), which is security definer. A direct
-- INSERT skips rooms_guard (it runs on UPDATE), so a client could otherwise
-- create a room that's already finished, or one roll from it, with itself as
-- the winner and claim a coupon for it.
drop policy if exists rooms_insert_host on public.rooms;
revoke insert on public.rooms from public, anon, authenticated;

commit;

-- Check: only coupons_select_own should be listed.
-- select policyname, cmd from pg_policies where tablename = 'coupons';
