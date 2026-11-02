-- A winner can collect a coupon after the match.
-- Run this whole file once in the Supabase SQL editor, AFTER 0010.
-- Safe to run again. All or nothing.
--
-- Until now a coupon was only claimed while the winner's app was watching the
-- room, and a finished room can't be re-entered, so closing the app in time
-- lost the coupon for good. Now:
--  1. claim_pending_coupons() mints a coupon for every win the caller hasn't
--     been paid for, then returns all of their coupons. It takes no arguments,
--     and goes through claim_room_coupon(), so a sweep can't create anything a
--     claim couldn't, and one coupon per room still holds.
--  2. Wins from before this file don't pay out. Until 0009 reached this
--     database a Snakes & Ladders winner_id was written by the app, so those
--     wins aren't proof of anything, and some were already paid by the old app
--     with a coupon that has no room_id. Coupons can't be taken back, so every
--     such room is closed here, in coupon_ineligible_rooms, and
--     claim_room_coupon() refuses it too. This is a list of rooms, not a date
--     check, because a player can still bump a room's updated_at.
--     verified_from keeps a re-run from closing newer wins. A migration that
--     adds a game to coupon_verified_games must close that game's rooms too.
--  3. Clients can't DELETE a room. The app never did, and a losing host could
--     otherwise delete a finished room to erase the winner's unpaid win.
--
-- To pay a closed room after checking it was a real win (a re-run closes it again):
--   delete from public.coupon_ineligible_rooms where room_id = '<room id>';

begin;

-- 2. Close every unpaid win from before this file.
alter table public.coupon_verified_games
  add column if not exists verified_from timestamptz;

update public.coupon_verified_games
set verified_from = now()
where verified_from is null;

create table if not exists public.coupon_ineligible_rooms (
  room_id uuid primary key references public.rooms (id) on delete cascade
);

alter table public.coupon_ineligible_rooms enable row level security;

revoke all on public.coupon_ineligible_rooms from public, anon, authenticated;

insert into public.coupon_ineligible_rooms (room_id)
select r.id
from public.rooms r
join public.coupon_verified_games g on g.game_id = r.game_id
where r.status = 'finished'
  and r.updated_at < g.verified_from
  and not exists (select 1 from public.coupons c where c.room_id = r.id)
on conflict (room_id) do nothing;

-- As in 0010, plus the coupon_ineligible_rooms check.
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
  if exists (select 1 from public.coupon_ineligible_rooms where room_id = v_room.id) then
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

-- 1. The sweep. Newest first, the order the wallet shows.
create or replace function public.claim_pending_coupons()
returns setof public.coupons
language plpgsql
security definer
set search_path = public
as $$
declare
  v_me uuid := auth.uid();
  v_room_id uuid;
begin
  if v_me is null then
    raise exception 'Not authenticated';
  end if;

  for v_room_id in
    select r.id
    from public.rooms r
    join public.coupon_verified_games g on g.game_id = r.game_id
    where r.status = 'finished'
      and r.winner_id = v_me
      and not exists (select 1 from public.coupons c where c.room_id = r.id)
      and not exists (select 1 from public.coupon_ineligible_rooms i where i.room_id = r.id)
  loop
    perform public.claim_room_coupon(v_room_id);
  end loop;

  return query
    select * from public.coupons where user_id = v_me order by created_at desc;
end;
$$;

revoke all on function public.claim_pending_coupons() from public, anon;
grant execute on function public.claim_pending_coupons() to authenticated;

-- Called on every launch, so finding a player's wins can't scan every room.
create index if not exists rooms_finished_winner_idx
  on public.rooms (winner_id)
  where status = 'finished';

-- 3. A room can't be deleted from the app.
drop policy if exists rooms_delete_host on public.rooms;
revoke delete on public.rooms from public, anon, authenticated;

commit;

-- Check: how many past wins this file closed.
-- select count(*) from public.coupon_ineligible_rooms;
