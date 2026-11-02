-- Self-exclusion can't be undone from the app, and it stops new online games.
-- Run this whole file once in the Supabase SQL editor. Do not run 0003 again
-- afterwards: it would restore matchmake() without this check.
-- Safe to run again. All or nothing.
--
-- Until now self_excluded was only a setting: a player could clear it with a
-- normal profile UPDATE, and nothing on the server read it. Now:
--  1. A client can set self_excluded but not clear it. The trigger goes by
--     current_user, so there's no setting to spoof: a client request runs as
--     anon or authenticated, and a security definer function as its owner.
--  2. Only reinstate_self_excluded() is meant to clear it. Clients can't call
--     it; run it from the SQL editor:
--       select public.reinstate_self_excluded('<user id>');
--  3. matchmake(), the only way into an online game, refuses an excluded
--     caller and won't pair anyone with an excluded player still in the queue.
--     A match already under way can finish.
--  4. Clients can't DELETE a profile, or they could delete the flag with it.
--     0012 revokes this too; it's here so this file doesn't depend on 0012.
--
-- The check reads "any row for this user", because this repo has no definition
-- of profiles and so can't promise user_id is unique. "is true" treats a null
-- as not excluded.

begin;

-- 1. One-way from the client.
create or replace function public.profiles_self_exclusion_guard()
returns trigger
language plpgsql
as $$
begin
  if old.self_excluded is true
     and new.self_excluded is not true
     and current_user in ('anon', 'authenticated') then
    raise exception 'Self-exclusion can only be lifted by support';
  end if;
  return new;
end;
$$;

drop trigger if exists profiles_self_exclusion_guard on public.profiles;
create trigger profiles_self_exclusion_guard
  before update on public.profiles
  for each row execute function public.profiles_self_exclusion_guard();

-- 2. Admin-only reinstatement.
create or replace function public.reinstate_self_excluded(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.profiles
  set self_excluded = false, updated_at = now()
  where user_id = p_user_id and self_excluded is true;

  if not found then
    raise exception 'That user is not self-excluded';
  end if;
end;
$$;

revoke all on function public.reinstate_self_excluded(uuid) from public, anon, authenticated;

-- 3. As in 0003, plus the two self_excluded checks.
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
  if exists (select 1 from public.profiles where user_id = v_me and self_excluded is true) then
    raise exception 'Self-excluded: this account can''t start a game';
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
  select q.user_id into v_opponent
  from public.matchmaking_queue q
  where q.game_id = p_game_id
    and q.room_id is null
    and q.user_id <> v_me
    and q.last_seen > now() - interval '10 seconds'
    and not exists (
      select 1 from public.profiles p
      where p.user_id = q.user_id and p.self_excluded is true
    )
  order by q.created_at
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

revoke all on function public.matchmake(text) from public, anon;
grant execute on function public.matchmake(text) to authenticated;

-- 4. A profile can't be deleted from the app.
revoke delete, truncate on public.profiles from public, anon, authenticated;

commit;

-- Check: the trigger exists, and only the owner (and Supabase's service_role)
-- can run the reinstatement.
-- select tgname from pg_trigger where tgname = 'profiles_self_exclusion_guard';
-- select grantee, privilege_type from information_schema.role_routine_grants
-- where routine_schema = 'public' and routine_name = 'reinstate_self_excluded';
