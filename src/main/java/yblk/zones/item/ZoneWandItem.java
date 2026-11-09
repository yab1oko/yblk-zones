package yblk.zones.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ПКМ по блоку — начать черновик / добавить ребро.
 * Alt+колёсико — двигать грань. R — настройки. Воздух — сброс черновика.
 */
public class ZoneWandItem extends Item {
    public ZoneWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        boolean sneak = player.isShiftKeyDown();
        if (ctx.getLevel().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    yblk.zones.client.ClientWand.onRightClickBlock(clicked, face, sneak));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // Only air-miss cancels draft. Block clicks go through useOn;
            // looking at the draft volume must not wipe / "delete" the selection.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    yblk.zones.client.ClientWand.onUseAir(player.isShiftKeyDown()));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.yblk_zones.zone_wand.desc"));
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                yblk.zones.client.ClientWand.appendCornerTip(tip));
    }
}
