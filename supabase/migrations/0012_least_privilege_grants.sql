-- Clients keep only the table privileges the app uses.
-- Run this whole file once in the Supabase SQL editor. It doesn't depend on 0011.
-- Safe to run again. All or nothing.
--
-- RLS already refuses these writes, so this changes nothing for the app. It's a
-- second lock, for the day a policy is changed by mistake:
--  1. games is read-only to clients. The lobby reads it before sign-in, so anon
--     keeps SELECT.
--  2. matchmaking_queue only changes through matchmake() and
--     cancel_matchmaking(), which are security definer and don't need these
--     grants.
--  3. profiles keeps INSERT and UPDATE for authenticated: the app creates a
--     player's row on first read and saves their settings to it. Nothing
--     deletes one.
--
-- coupons, rooms and the coupon_* tables are locked by 0010 and 0011.

begin;

revoke insert, update, delete, truncate, references, trigger
  on public.games, public.matchmaking_queue, public.profiles
  from public, anon, authenticated;

grant insert, update on public.profiles to authenticated;

commit;

-- Check: anon should have only SELECT on all three, and authenticated only
-- SELECT, plus INSERT and UPDATE on profiles.
-- select table_name, grantee, privilege_type
-- from information_schema.role_table_grants
-- where table_schema = 'public'
--   and table_name in ('games', 'matchmaking_queue', 'profiles')
--   and grantee in ('PUBLIC', 'anon', 'authenticated')
-- order by 1, 2, 3;
