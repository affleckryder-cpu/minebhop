package com.minebhop.gui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;

/**
 * The few drawing calls MineBhop makes, in the shape of the GuiGraphics class Minecraft 1.20 added,
 * on top of 1.19.2's PoseStack and GuiComponent. With it the HUD renderer is shared with every
 * other version unchanged, and the settings screen differs from 1.20.1's mostly in its method
 * signatures.
 */
public final class GuiGraphics {

	private final PoseStack pose;

	public GuiGraphics(PoseStack pose) {
		this.pose = pose;
	}

	public PoseStack pose() {
		return pose;
	}

	public int guiWidth() {
		return Minecraft.getInstance().getWindow().getGuiScaledWidth();
	}

	public int guiHeight() {
		return Minecraft.getInstance().getWindow().getGuiScaledHeight();
	}

	public void fill(int left, int top, int right, int bottom, int colour) {
		GuiComponent.fill(pose, left, top, right, bottom, colour);
	}

	/** A one-pixel border around the rectangle. */
	public void renderOutline(int x, int y, int width, int height, int colour) {
		fill(x, y, x + width, y + 1, colour);
		fill(x, y + height - 1, x + width, y + height, colour);
		fill(x, y + 1, x + 1, y + height - 1, colour);
		fill(x + width - 1, y + 1, x + width, y + height - 1, colour);
	}

	public int drawString(Font font, String text, int x, int y, int colour, boolean shadow) {
		return shadow ? font.drawShadow(pose, text, x, y, colour) : font.draw(pose, text, x, y, colour);
	}

	public int drawString(Font font, Component text, int x, int y, int colour, boolean shadow) {
		return shadow ? font.drawShadow(pose, text, x, y, colour) : font.draw(pose, text, x, y, colour);
	}

	/** Clips drawing to a rectangle given in GUI coordinates, until {@link #disableScissor()}. */
	public void enableScissor(int left, int top, int right, int bottom) {
		Window window = Minecraft.getInstance().getWindow();
		double scale = window.getGuiScale();
		RenderSystem.enableScissor((int) (left * scale), (int) (window.getHeight() - bottom * scale),
				Math.max(0, (int) ((right - left) * scale)), Math.max(0, (int) ((bottom - top) * scale)));
	}

	public void disableScissor() {
		RenderSystem.disableScissor();
	}
}
