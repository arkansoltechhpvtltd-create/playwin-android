-- Chinese Checkers gets online play.
-- Run this whole file once in the Supabase SQL editor, AFTER 0006.
--
-- 0006 kept CHINESE_CHECKERS out of the matchmaking queue while it was
-- vs-Computer only. This lets it back in; Connect 4 (retired) and the
-- single-player games stay out. Nothing else is needed: matchmake() already
-- creates a room with board_state '{}' for any game other than Connect 4,
-- and the app reads '{}' as the starting position. rooms_guard already makes
-- sure only the player whose turn it is can write a move.

create or replace function public.matchmaking_queue_single_player_guard()
returns trigger
language plpgsql
as $$
begin
  if new.game_id in ('SUDOKU', 'TWENTY_FORTY_EIGHT', 'CONNECT_FOUR') then
    raise exception 'Invalid game';
  end if;
  return new;
end;
$$;
