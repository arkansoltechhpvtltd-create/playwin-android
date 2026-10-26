-- Snakes & Ladders replaces Word Hunt as the ninth game.
-- Run this whole file once in the Supabase SQL editor, AFTER 0007.
--
-- 1. The lobby catalogue: Word Hunt's row becomes Snakes & Ladders, keeping
--    its sort_order, so it stays in the same lobby slot. The app maps rows by
--    id, so until this runs it skips the unknown WORD_HUNT row and the lobby
--    shows only eight games.
-- 2. Word Hunt's online play is retired: anyone still queued is dropped and
--    any open Word Hunt room is closed (a status-only write, which rooms_guard
--    allows).
-- 3. WORD_HUNT can no longer be queued for. SNAKES_AND_LADDERS needs nothing
--    new: matchmake() already creates a room with board_state '{}' for it
--    (read by the app as the starting position), and rooms_guard already
--    makes sure only the player whose turn it is can write a roll.

begin;

-- 1. Catalogue
update public.games
set id = 'SNAKES_AND_LADDERS', name = 'Snakes & Ladders', playable = true
where id = 'WORD_HUNT';

-- (If there was no WORD_HUNT row, add Snakes & Ladders at the end of the list instead.)
insert into public.games (id, name, playable, sort_order)
select 'SNAKES_AND_LADDERS', 'Snakes & Ladders', true, (select coalesce(max(sort_order), 0) + 1 from public.games)
where not exists (select 1 from public.games where id = 'SNAKES_AND_LADDERS');

-- 2. Retire Word Hunt online play
delete from public.matchmaking_queue where game_id = 'WORD_HUNT';

update public.rooms
set status = 'abandoned'
where game_id = 'WORD_HUNT' and status in ('waiting', 'active');

-- 3. Games with no online play never enter the matchmaking queue
create or replace function public.matchmaking_queue_single_player_guard()
returns trigger
language plpgsql
as $$
begin
  if new.game_id in ('SUDOKU', 'TWENTY_FORTY_EIGHT', 'CONNECT_FOUR', 'WORD_HUNT') then
    raise exception 'Invalid game';
  end if;
  return new;
end;
$$;

commit;

-- Check: the lobby list, in order.
-- select id, name, playable, sort_order from public.games order by sort_order;
