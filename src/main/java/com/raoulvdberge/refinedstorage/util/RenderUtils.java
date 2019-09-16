package com.raoulvdberge.refinedstorage.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.MissingTextureSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.config.GuiUtils;

import javax.annotation.Nonnull;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class RenderUtils {
    public static final Matrix4f EMPTY_MATRIX_TRANSFORM = getTransform(0, 0, 0, 0, 0, 0, 1.0f).getMatrix(Direction.DOWN); // TODO: dir?

    // @Volatile: From ForgeBlockStateV1
    private static final TRSRTransformation FLIP_X = new TRSRTransformation(null, null, new Vector3f(-1, 1, 1), null);

    private static ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> DEFAULT_ITEM_TRANSFORM;
    private static ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> DEFAULT_BLOCK_TRANSFORM;

    private static final VertexFormat ITEM_FORMAT_WITH_LIGHTMAP = new VertexFormat(DefaultVertexFormats.ITEM).addElement(DefaultVertexFormats.TEX_2S);

    public static String shorten(String text, int length) {
        if (text.length() > length) {
            text = text.substring(0, length) + "...";
        }
        return text;
    }

    private static TRSRTransformation leftifyTransform(TRSRTransformation transform) {
        return TRSRTransformation.blockCenterToCorner(FLIP_X.compose(TRSRTransformation.blockCornerToCenter(transform)).compose(FLIP_X));
    }

    private static TRSRTransformation getTransform(float tx, float ty, float tz, float ax, float ay, float az, float s) {
        return new TRSRTransformation(
            new Vector3f(tx / 16, ty / 16, tz / 16),
            TRSRTransformation.quatFromXYZDegrees(new Vector3f(ax, ay, az)),
            new Vector3f(s, s, s),
            null
        );
    }

    public static ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> getDefaultItemTransforms() {
        if (DEFAULT_ITEM_TRANSFORM != null) {
            return DEFAULT_ITEM_TRANSFORM;
        }

        return DEFAULT_ITEM_TRANSFORM = ImmutableMap.<ItemCameraTransforms.TransformType, TRSRTransformation>builder()
            .put(ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, getTransform(0, 3, 1, 0, 0, 0, 0.55f))
            .put(ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND, getTransform(0, 3, 1, 0, 0, 0, 0.55f))
            .put(ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND, getTransform(1.13f, 3.2f, 1.13f, 0, -90, 25, 0.68f))
            .put(ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND, getTransform(1.13f, 3.2f, 1.13f, 0, 90, -25, 0.68f))
            .put(ItemCameraTransforms.TransformType.GROUND, getTransform(0, 2, 0, 0, 0, 0, 0.5f))
            .put(ItemCameraTransforms.TransformType.HEAD, getTransform(0, 13, 7, 0, 180, 0, 1))
            .build();
    }

    public static ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> getDefaultBlockTransforms() {
        if (DEFAULT_BLOCK_TRANSFORM != null) {
            return DEFAULT_BLOCK_TRANSFORM;
        }

        TRSRTransformation thirdperson = getTransform(0, 2.5f, 0, 75, 45, 0, 0.375f);

        return DEFAULT_BLOCK_TRANSFORM = ImmutableMap.<ItemCameraTransforms.TransformType, TRSRTransformation>builder()
            .put(ItemCameraTransforms.TransformType.GUI, getTransform(0, 0, 0, 30, 225, 0, 0.625f))
            .put(ItemCameraTransforms.TransformType.GROUND, getTransform(0, 3, 0, 0, 0, 0, 0.25f))
            .put(ItemCameraTransforms.TransformType.FIXED, getTransform(0, 0, 0, 0, 0, 0, 0.5f))
            .put(ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, thirdperson)
            .put(ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND, leftifyTransform(thirdperson))
            .put(ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND, getTransform(0, 0, 0, 0, 45, 0, 0.4f))
            .put(ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND, getTransform(0, 0, 0, 0, 225, 0, 0.4f))
            .build();
    }

    public static int getOffsetOnScale(int pos, float scale) {
        float multiplier = (pos / scale);

        return (int) multiplier;
    }

    public static void addCombinedItemsToTooltip(List<ITextComponent> tooltip, boolean displayAmount, List<ItemStack> stacks) {
        Set<Integer> combinedIndices = new HashSet<>();

        for (int i = 0; i < stacks.size(); ++i) {
            if (!stacks.get(i).isEmpty() && !combinedIndices.contains(i)) {
                ItemStack stack = stacks.get(i);

                ITextComponent data = stack.getDisplayName();

                int amount = stack.getCount();

                for (int j = i + 1; j < stacks.size(); ++j) {
                    if (API.instance().getComparer().isEqual(stack, stacks.get(j))) {
                        amount += stacks.get(j).getCount();

                        combinedIndices.add(j);
                    }
                }

                if (displayAmount) {
                    data = new StringTextComponent(amount + "x ").appendSibling(data);
                }

                tooltip.add(data.setStyle(new Style().setColor(TextFormatting.GRAY)));
            }
        }
    }

    public static void addCombinedFluidsToTooltip(List<ITextComponent> tooltip, boolean displayMb, NonNullList<FluidStack> stacks) {
        Set<Integer> combinedIndices = new HashSet<>();

        for (int i = 0; i < stacks.size(); ++i) {
            if (!stacks.get(i).isEmpty() && !combinedIndices.contains(i)) {
                FluidStack stack = stacks.get(i);

                ITextComponent data = stack.getDisplayName();

                int amount = stack.getAmount();

                for (int j = i + 1; j < stacks.size(); ++j) {
                    if (API.instance().getComparer().isEqual(stack, stacks.get(j), IComparer.COMPARE_NBT)) {
                        amount += stacks.get(j).getAmount();

                        combinedIndices.add(j);
                    }
                }

                if (displayMb) {
                    data = new StringTextComponent(API.instance().getQuantityFormatter().formatInBucketForm(amount) + " ").appendSibling(data);
                }

                tooltip.add(data.setStyle(new Style().setColor(TextFormatting.GRAY)));
            }
        }
    }

    // @Volatile: Copied with some tweaks from GuiUtils#drawHoveringText(@Nonnull final ItemStack stack, List<String> textLines, int mouseX, int mouseY, int screenWidth, int screenHeight, int maxTextWidth, FontRenderer font)
    public static void drawTooltipWithSmallText(List<String> textLines, List<String> smallTextLines, boolean showSmallText, @Nonnull ItemStack stack, int mouseX, int mouseY, int screenWidth, int screenHeight, FontRenderer fontRenderer) {
        if (!textLines.isEmpty()) {
            RenderTooltipEvent.Pre event = new RenderTooltipEvent.Pre(stack, textLines, mouseX, mouseY, screenWidth, screenHeight, -1, fontRenderer);
            if (MinecraftForge.EVENT_BUS.post(event)) {
                return;
            }

            mouseX = event.getX();
            mouseY = event.getY();

            screenWidth = event.getScreenWidth();
            screenHeight = event.getScreenHeight();

            FontRenderer font = event.getFontRenderer();

            // RS BEGIN
            //float textScale = font.getUnicodeFlag() ? 1F : 0.7F;
            float textScale = 1F;
            // RS END

            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.disableDepthTest();
            int tooltipTextWidth = 0;

            for (String textLine : textLines) {
                int textLineWidth = font.getStringWidth(textLine);

                if (textLineWidth > tooltipTextWidth) {
                    tooltipTextWidth = textLineWidth;
                }
            }

            // RS BEGIN
            if (showSmallText) {
                int size;

                for (String smallText : smallTextLines) {
                    size = (int) (font.getStringWidth(smallText) * textScale);

                    if (size > tooltipTextWidth) {
                        tooltipTextWidth = size;
                    }
                }
            }
            // RS END

            int titleLinesCount = 1;
            int tooltipX = mouseX + 12;

            int tooltipY = mouseY - 12;
            int tooltipHeight = 8;

            if (textLines.size() > 1) {
                tooltipHeight += (textLines.size() - 1) * 10;
                if (textLines.size() > titleLinesCount) {
                    tooltipHeight += 2;
                }
            }

            // RS BEGIN
            if (showSmallText) {
                tooltipHeight += smallTextLines.size() * 10;
            }
            // RS END

            if (tooltipY + tooltipHeight + 6 > screenHeight) {
                tooltipY = screenHeight - tooltipHeight - 6;
            }

            final int zLevel = 300;
            final int backgroundColor = 0xF0100010;
            GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY - 4, tooltipX + tooltipTextWidth + 3, tooltipY - 3, backgroundColor, backgroundColor);
            GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY + tooltipHeight + 3, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 4, backgroundColor, backgroundColor);
            GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY - 3, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 3, backgroundColor, backgroundColor);
            GuiUtils.drawGradientRect(zLevel, tooltipX - 4, tooltipY - 3, tooltipX - 3, tooltipY + tooltipHeight + 3, backgroundColor, backgroundColor);
            GuiUtils.drawGradientRect(zLevel, tooltipX + tooltipTextWidth + 3, tooltipY - 3, tooltipX + tooltipTextWidth + 4, tooltipY + tooltipHeight + 3, backgroundColor, backgroundColor);
            final int borderColorStart = 0x505000FF;
            final int borderColorEnd = (borderColorStart & 0xFEFEFE) >> 1 | borderColorStart & 0xFF000000;
            GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY - 3 + 1, tooltipX - 3 + 1, tooltipY + tooltipHeight + 3 - 1, borderColorStart, borderColorEnd);
            GuiUtils.drawGradientRect(zLevel, tooltipX + tooltipTextWidth + 2, tooltipY - 3 + 1, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 3 - 1, borderColorStart, borderColorEnd);
            GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY - 3, tooltipX + tooltipTextWidth + 3, tooltipY - 3 + 1, borderColorStart, borderColorStart);
            GuiUtils.drawGradientRect(zLevel, tooltipX - 3, tooltipY + tooltipHeight + 2, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 3, borderColorEnd, borderColorEnd);

            MinecraftForge.EVENT_BUS.post(new RenderTooltipEvent.PostBackground(stack, textLines, tooltipX, tooltipY, font, tooltipTextWidth, tooltipHeight));
            int tooltipTop = tooltipY;

            for (int lineNumber = 0; lineNumber < textLines.size(); ++lineNumber) {
                String line = textLines.get(lineNumber);
                font.drawStringWithShadow(line, (float) tooltipX, (float) tooltipY, -1);

                if (lineNumber + 1 == titleLinesCount) {
                    tooltipY += 2;
                }

                tooltipY += 10;
            }

            MinecraftForge.EVENT_BUS.post(new RenderTooltipEvent.PostText(stack, textLines, tooltipX, tooltipTop, font, tooltipTextWidth, tooltipHeight));

            // RS BEGIN
            if (showSmallText) {
                GlStateManager.pushMatrix();
                GlStateManager.scalef(textScale, textScale, 1);

                int y = tooltipTop + tooltipHeight - 6;

                for (int i = smallTextLines.size() - 1; i >= 0; --i) {
                    font.drawStringWithShadow(
                        TextFormatting.GRAY + smallTextLines.get(i),
                        RenderUtils.getOffsetOnScale(tooltipX, textScale),
                        RenderUtils.getOffsetOnScale(y - (/* TODO font.getUnicodeFlag() ? 2 : */0), textScale),
                        -1
                    );

                    y -= 9;
                }

                GlStateManager.popMatrix();
            }
            // RS END

            GlStateManager.enableLighting();
            GlStateManager.enableDepthTest();
            RenderHelper.enableStandardItemLighting();
            GlStateManager.enableRescaleNormal();
        }
    }

    // @Volatile: From Screen#getTooltipFromItem
    public static List<String> getTooltipFromItem(ItemStack stack) {
        List<ITextComponent> tooltip = stack.getTooltip(Minecraft.getInstance().player, Minecraft.getInstance().gameSettings.advancedItemTooltips ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL);
        List<String> tooltipStrings = Lists.newArrayList();

        for (ITextComponent itextcomponent : tooltip) {
            tooltipStrings.add(itextcomponent.getFormattedText());
        }

        return tooltipStrings;
    }

    public static boolean isLightMapDisabled() {
        return false;
        // TODO return FMLClientHandler.instance().hasOptifine() || !ForgeModContainer.forgeLightPipelineEnabled;
    }

    public static VertexFormat getFormatWithLightMap(VertexFormat format) {
        if (isLightMapDisabled()) {
            return format;
        }

        if (format == DefaultVertexFormats.BLOCK) {
            return DefaultVertexFormats.BLOCK;
        } else if (format == DefaultVertexFormats.ITEM) {
            return ITEM_FORMAT_WITH_LIGHTMAP;
        } else if (!format.hasUv(1)) {
            VertexFormat result = new VertexFormat(format);

            result.addElement(DefaultVertexFormats.TEX_2S);

            return result;
        }

        return format;
    }

    public static TextureAtlasSprite getSprite(IBakedModel coverModel, BlockState coverState, Direction facing, long rand) {
        TextureAtlasSprite sprite = null;

        BlockRenderLayer originalLayer = MinecraftForgeClient.getRenderLayer();

        try {
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                ForgeHooksClient.setRenderLayer(layer);

                for (BakedQuad bakedQuad : coverModel.getQuads(coverState, facing, new Random())) {
                    return bakedQuad.getSprite();
                }

                for (BakedQuad bakedQuad : coverModel.getQuads(coverState, null, new Random())) { // TODO random inst
                    if (sprite == null) {
                        sprite = bakedQuad.getSprite();
                    }

                    if (bakedQuad.getFace() == facing) {
                        return bakedQuad.getSprite();
                    }
                }
            }
        } catch (Exception e) {
            // NO OP
        } finally {
            ForgeHooksClient.setRenderLayer(originalLayer);
        }

        if (sprite == null) {
            try {
                sprite = coverModel.getParticleTexture();
            } catch (Exception e) {
                // NO OP
            }
        }

        if (sprite == null) {
            sprite = MissingTextureSprite.func_217790_a(); // TODO Mapping
        }

        return sprite;
    }

    public static boolean inBounds(int x, int y, int w, int h, double ox, double oy) {
        return ox >= x && ox <= x + w && oy >= y && oy <= y + h;
    }
}