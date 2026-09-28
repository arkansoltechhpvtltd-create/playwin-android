-- Only the player whose turn it is may hand the turn on.
-- Run this whole file once in the Supabase SQL editor, AFTER 0003.
--
-- Until now rooms_guard only checked the caller's turn when the board
-- changed, so a write that changed current_turn *without* a move - which
-- Word Hunt uses to pass a timed-out turn - was accepted from either
-- player, letting the waiting player hand the turn to themselves. Now any
-- change to current_turn must come from the current_turn player too.
-- Status-only writes (leaving/abandoning) are still allowed from either side.

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

  if new.board_state is distinct from old.board_state
     or new.current_turn is distinct from old.current_turn then
    if old.status <> 'active' then
      raise exception 'Room is not active';
    end if;
    if old.current_turn is null or auth.uid() is distinct from old.current_turn then
      raise exception 'Not your turn';
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
