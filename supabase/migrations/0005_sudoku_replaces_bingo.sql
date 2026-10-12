-- Sudoku replaces Bingo as the ninth game.
-- Run this whole file once in the Supabase SQL editor, AFTER 0004.
--
-- 1. The lobby catalogue: Bingo's row becomes Sudoku, keeping its place in
--    the list. The app maps rows by id, so until this runs it skips the
--    unknown BINGO row and the lobby shows only eight games.
-- 2. Bingo's online play is retired: anyone still queued is dropped and any
--    open Bingo room is closed (a status-only write, which rooms_guard allows).
-- 3. Sudoku (like 2048) is single-player, so the queue refuses it outright.
--    matchmake() validates only the id's shape, so without this a crafted
--    call could still queue for it. Other games are untouched.

begin;

-- 1. Catalogue
update public.games
set id = 'SUDOKU', name = 'Sudoku', playable = true
where id = 'BINGO';

-- (If there was no BINGO row, add Sudoku at the end of the list instead.)
insert into public.games (id, name, playable, sort_order)
select 'SUDOKU', 'Sudoku', true, (select coalesce(max(sort_order), 0) + 1 from public.games)
where not exists (select 1 from public.games where id = 'SUDOKU');

-- 2. Retire Bingo online play
delete from public.matchmaking_queue where game_id = 'BINGO';

update public.rooms
set status = 'abandoned'
where game_id = 'BINGO' and status in ('waiting', 'active');

-- 3. Single-player games never enter the matchmaking queue
create or replace function public.matchmaking_queue_single_player_guard()
returns trigger
language plpgsql
as $$
begin
  if new.game_id in ('SUDOKU', 'TWENTY_FORTY_EIGHT') then
    raise exception 'Invalid game';
  end if;
  return new;
end;
$$;

drop trigger if exists matchmaking_queue_single_player_guard on public.matchmaking_queue;
create trigger matchmaking_queue_single_player_guard
  before insert or update of game_id on public.matchmaking_queue
  for each row execute function public.matchmaking_queue_single_player_guard();

commit;

-- Check: the lobby list, in order.
-- select id, name, playable, sort_order from public.games order by sort_order;
