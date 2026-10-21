-- Chinese Checkers replaces Connect 4 as the ninth game.
-- Run this whole file once in the Supabase SQL editor, AFTER 0005.
--
-- 1. The lobby catalogue: Connect 4's row becomes Chinese Checkers, keeping
--    its sort_order, so it stays in the same lobby slot. The app maps rows by
--    id, so until this runs it skips the unknown CONNECT_FOUR row and the
--    lobby shows only eight games.
-- 2. Connect 4's online play is retired: anyone still queued is dropped and
--    any open Connect 4 room is closed (a status-only write, which rooms_guard
--    allows).
-- 3. Neither game can be queued for: Connect 4 is gone, and Chinese Checkers
--    is vs-Computer only for now. matchmake() validates only the id's shape,
--    so without this a crafted call could still queue for them. Take
--    CHINESE_CHECKERS out of this list when its online mode ships.

begin;

-- 1. Catalogue
update public.games
set id = 'CHINESE_CHECKERS', name = 'Chinese Checkers', playable = true
where id = 'CONNECT_FOUR';

-- (If there was no CONNECT_FOUR row, add Chinese Checkers at the end of the list instead.)
insert into public.games (id, name, playable, sort_order)
select 'CHINESE_CHECKERS', 'Chinese Checkers', true, (select coalesce(max(sort_order), 0) + 1 from public.games)
where not exists (select 1 from public.games where id = 'CHINESE_CHECKERS');

-- 2. Retire Connect 4 online play
delete from public.matchmaking_queue where game_id = 'CONNECT_FOUR';

update public.rooms
set status = 'abandoned'
where game_id = 'CONNECT_FOUR' and status in ('waiting', 'active');

-- 3. Games with no online play never enter the matchmaking queue
create or replace function public.matchmaking_queue_single_player_guard()
returns trigger
language plpgsql
as $$
begin
  if new.game_id in ('SUDOKU', 'TWENTY_FORTY_EIGHT', 'CONNECT_FOUR', 'CHINESE_CHECKERS') then
    raise exception 'Invalid game';
  end if;
  return new;
end;
$$;

commit;

-- Check: the lobby list, in order.
-- select id, name, playable, sort_order from public.games order by sort_order;
