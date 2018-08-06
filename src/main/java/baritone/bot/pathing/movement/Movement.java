package baritone.bot.pathing.movement;

import baritone.bot.Baritone;
import baritone.bot.behavior.impl.LookBehavior;
import baritone.bot.behavior.impl.LookBehaviorUtils;
import baritone.bot.pathing.movement.MovementState.MovementStatus;
import baritone.bot.utils.BlockStateInterface;
import baritone.bot.utils.Helper;
import baritone.bot.utils.Rotation;
import baritone.bot.utils.ToolSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Optional;

import static baritone.bot.InputOverrideHandler.Input;

public abstract class Movement implements Helper, MovementHelper {

    private MovementState currentState = new MovementState().setStatus(MovementStatus.PREPPING);
    protected final BlockPos src;

    protected final BlockPos dest;

    /**
     * The positions that need to be broken before this movement can ensue
     */
    public final BlockPos[] positionsToBreak;

    /**
     * The positions where we need to place a block before this movement can ensue
     */
    public final BlockPos[] positionsToPlace;

    private Double cost;

    protected Movement(BlockPos src, BlockPos dest, BlockPos[] toBreak, BlockPos[] toPlace) {
        this.src = src;
        this.dest = dest;
        this.positionsToBreak = toBreak;
        this.positionsToPlace = toPlace;
    }

    protected Movement(BlockPos src, BlockPos dest, BlockPos[] toBreak, BlockPos[] toPlace, Vec3d rotationTarget) {
        this(src, dest, toBreak, toPlace);
    }

    public double getCost(ToolSet ts) {
        if (cost == null) {
            if (ts == null) {
                ts = new ToolSet();
            }
            cost = calculateCost(ts);
        }
        return cost;
    }

    protected abstract double calculateCost(ToolSet ts); // TODO pass in information like whether it's allowed to place throwaway blocks

    public double recalculateCost() {
        cost = null;
        return getCost(null);
    }

    /**
     * Handles the execution of the latest Movement
     * State, and offers a Status to the calling class.
     *
     * @return Status
     */
    public MovementStatus update() {
        MovementState latestState = updateState(currentState);
        latestState.getTarget().getRotation().ifPresent(LookBehavior.INSTANCE::updateTarget);
        // TODO: calculate movement inputs from latestState.getGoal().position
        // latestState.getTarget().position.ifPresent(null);      NULL CONSUMER REALLY SHOULDN'T BE THE FINAL THING YOU SHOULD REALLY REPLACE THIS WITH ALMOST ACTUALLY ANYTHING ELSE JUST PLEASE DON'T LEAVE IT AS IT IS THANK YOU KANYE
        latestState.inputState.forEach((input, forced) -> {
            Baritone.INSTANCE.getInputOverrideHandler().setInputForceState(input, forced);
            System.out.println(input + " AND " + forced);
        });
        latestState.inputState.replaceAll((input, forced) -> false);
        currentState = latestState;

        if (isFinished())
            onFinish(latestState);

        return currentState.getStatus();
    }


    private boolean prepared(MovementState state) {
        if (state.getStatus() == MovementStatus.WAITING)
            return true;

        for (BlockPos blockPos : positionsToBreak) {
            if (!MovementHelper.canWalkThrough(blockPos, BlockStateInterface.get(blockPos))) {
                Optional<Rotation> reachable = LookBehaviorUtils.reachable(blockPos);
                if (reachable.isPresent()) {
                    state.setTarget(new MovementState.MovementTarget(reachable.get())).setInput(Input.CLICK_LEFT, true);
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isFinished() {
        return (currentState.getStatus() != MovementStatus.RUNNING
                && currentState.getStatus() != MovementStatus.PREPPING
                && currentState.getStatus() != MovementStatus.WAITING);
    }

    public BlockPos getSrc() {
        return src;
    }

    public BlockPos getDest() {
        return dest;
    }

    /**
     * Run cleanup on state finish and declare success.
     */
    public void onFinish(MovementState state) {
        state.inputState.replaceAll((input, forced) -> false);
        state.inputState.forEach((input, forced) -> Baritone.INSTANCE.getInputOverrideHandler().setInputForceState(input, forced));
        state.setStatus(MovementStatus.SUCCESS);
    }

    public void cancel() {
        currentState.inputState.replaceAll((input, forced) -> false);
        currentState.inputState.forEach((input, forced) -> Baritone.INSTANCE.getInputOverrideHandler().setInputForceState(input, forced));
        currentState.setStatus(MovementStatus.CANCELED);
    }

    /**
     * Calculate latest movement state.
     * Gets called once a tick.
     *
     * @return
     */
    public MovementState updateState(MovementState state) {
        if (!prepared(state))
            return state.setStatus(MovementStatus.PREPPING);
        else if (state.getStatus() == MovementStatus.PREPPING) {
            state.setStatus(MovementStatus.WAITING);
        }
        return state;
    }

    public ArrayList<BlockPos> toBreakCached = null;
    public ArrayList<BlockPos> toPlaceCached = null;

    public ArrayList<BlockPos> toBreak() {
        if (toBreakCached != null) {
            return toBreakCached;
        }
        ArrayList<BlockPos> result = new ArrayList<>();
        for (BlockPos positionsToBreak1 : positionsToBreak) {
            if (!MovementHelper.canWalkThrough(positionsToBreak1, BlockStateInterface.get(positionsToBreak1))) {
                result.add(positionsToBreak1);
            }
        }
        toBreakCached = result;
        return result;
    }

    public ArrayList<BlockPos> toPlace() {
        if (toPlaceCached != null) {
            return toPlaceCached;
        }
        ArrayList<BlockPos> result = new ArrayList<>();
        for (BlockPos positionsToPlace1 : positionsToPlace) {
            if (!MovementHelper.canWalkOn(positionsToPlace1)) {
                result.add(positionsToPlace1);
            }
        }
        toPlaceCached = result;
        return result;
    }
}
