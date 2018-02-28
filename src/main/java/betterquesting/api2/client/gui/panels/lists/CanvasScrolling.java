package betterquesting.api2.client.gui.panels.lists;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;
import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.client.gui.controls.IValueIO;
import betterquesting.api2.client.gui.misc.ComparatorGuiDepth;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiCanvas;
import betterquesting.api2.client.gui.panels.IGuiPanel;

public class CanvasScrolling implements IGuiCanvas
{
	private final List<IGuiPanel> guiPanels = new ArrayList<IGuiPanel>();
	private final IGuiRect transform;
	
	// Scrolling bounds
	private GuiRectangle scrollBounds = new GuiRectangle(0, 0, 0, 0);
	private boolean extendedScroll = false;
	private boolean zoomMode = false;
	private int margin = 0;
	// Scroll and zoom drivers
	private IValueIO<Float> scrollX;
	private IValueIO<Float> scrollY;
	private IValueIO<Float> zoomScale;
	
	private boolean isDragging = false; // Mouse buttons held for dragging
	private boolean hasDragged = false; // Dragging used. Don't fire onMouseRelease
	private int scrollSpeed = 12;
	
	// Starting drag scroll values
	private float dragSX = 0;
	private float dragSY = 0;
	// Starting drag mouse positions
	private int dragMX = 0;
	private int dragMY = 0;
	// Last known scroll position (unscaled)
	private int lsx = 0;
	private int lsy = 0;
	
	public CanvasScrolling(IGuiRect rect)
	{
		this.transform = rect;
		
		// Dummy value drivers
		
		scrollX = new IValueIO<Float>()
		{
			private float v = 0F;
			
			@Override
			public Float readValue()
			{
				return v;
			}
			
			@Override
			public void writeValue(Float value)
			{
				this.v = MathHelper.clamp(value, 0F, 1F);
			}
		};
		
		scrollY = new IValueIO<Float>()
		{
			private float v = 0F;
			
			@Override
			public Float readValue()
			{
				return v;
			}
			
			@Override
			public void writeValue(Float value)
			{
				this.v = MathHelper.clamp(value, 0F, 1F);
			}
		};
		
		zoomScale = new IValueIO<Float>()
		{
			private float v = 1F;
			
			@Override
			public Float readValue()
			{
				return v;
			}
			
			@Override
			public void writeValue(Float value)
			{
				this.v = MathHelper.clamp(value, 0.25F, 2F);
			}
		};
	}
	
	public CanvasScrolling setScrollDriverX(IValueIO<Float> driver)
	{
		this.scrollX = driver;
		return this;
	}
	
	public CanvasScrolling setScrollDriverY(IValueIO<Float> driver)
	{
		this.scrollY = driver;
		return this;
	}
	
	public CanvasScrolling setZoomDriver(IValueIO<Float> driver)
	{
		this.zoomScale = driver;
		return this;
	}
	
	public CanvasScrolling setScrollSpeed(int dx)
	{
		this.scrollSpeed = dx;
		return this;
	}
	
	public CanvasScrolling setupAdvanceScroll(boolean scrollToZoom, boolean extendedScroll, int scrollMargin)
	{
		this.zoomMode = scrollToZoom;
		this.extendedScroll = extendedScroll;
		this.margin = scrollMargin;
		return this;
	}
	
	public int getScrollX()
	{
		return Math.round(scrollBounds.getX() + scrollBounds.getWidth() * scrollX.readValue());
	}
	
	public int getScrollY()
	{
		return Math.round(scrollBounds.getY() + scrollBounds.getHeight() * scrollY.readValue());
	}
	
	@Override
	public void initPanel()
	{
	}
	
	@Override
	public IGuiRect getTransform()
	{
		return transform;
	}
	
	@Override
	public void drawPanel(int mx, int my, float partialTick)
	{
		float zs = zoomScale.readValue();
		
		if(isDragging)
		{
			int dx = (int)((dragMX - mx) / zs);
			int dy = (int)((dragMY - my) / zs);
			
			if(scrollBounds.getWidth() > 0)
			{
				float dsx = dx / (float)scrollBounds.getWidth() + dragSX;
				scrollX.writeValue(dsx);
				
				if(!hasDragged && Math.abs(dragSX - scrollX.readValue()) > 0.05F)
				{
					hasDragged = true;
				}
			}
			
			if(scrollBounds.getHeight() > 0)
			{
				float dsy = dy / (float)scrollBounds.getHeight() + dragSY;
				scrollY.writeValue(dsy);
				
				if(!hasDragged && Math.abs(dragSY - scrollY.readValue()) > 0.05F)
				{
					hasDragged = true;
				}
			}
		} else if(hasDragged)
		{
			hasDragged = false;
		}
		
		if(lsx != getScrollX() || lsy != getScrollY())
		{
			this.updatePanelScroll();
		}
		
		GlStateManager.pushMatrix();
		
		Minecraft mc = Minecraft.getMinecraft();
		RenderUtils.startScissor(mc, new GuiRectangle(transform));
		
		int tx = transform.getX();
		int ty = transform.getY();
		
		GlStateManager.translate(tx - lsx * zs, ty - lsy * zs, 0F);
		GlStateManager.scale(zs, zs, 1F);
		
		int smx = (int)((mx - tx + lsx) / zs);
		int smy = (int)((my - ty + lsy) / zs);
		List<IGuiPanel> tmp = new ArrayList<IGuiPanel>(guiPanels);
		
		for(IGuiPanel panel : tmp)
		{
			panel.drawPanel(smx, smy, partialTick);
		}
		
		RenderUtils.endScissor(mc);
		GlStateManager.popMatrix();
	}
	
	@Override
	public boolean onMouseClick(int mx, int my, int click)
	{
		if(!transform.contains(mx, my))
		{
			return false;
		}
		
		float zs = zoomScale.readValue();
		int tx = transform.getX();
		int ty = transform.getY();
		int smx = (int)((mx - tx + lsx) / zs);
		int smy = (int)((my - ty + lsy) / zs);
		
		List<IGuiPanel> tmp = new ArrayList<IGuiPanel>(guiPanels);
		Collections.reverse(tmp);
		boolean used = false;
		
		for(IGuiPanel panel : tmp)
		{
			used = panel.onMouseClick(smx, smy, click);
			
			if(used)
			{
				break;
			}
		}
		
		if(!used && (click == 0 || click == 2))
		{
			dragSX = scrollX.readValue();
			dragSY = scrollY.readValue();
			dragMX = mx;
			dragMY = my;
			isDragging = true;
		}
		
		return used;
	}
	
	@Override
	public boolean onMouseRelease(int mx, int my, int click)
	{
		boolean used = false;
		
		if(!hasDragged)
		{
			if(!transform.contains(mx, my))
			{
				return false;
			}
			
			float zs = zoomScale.readValue();
			int tx = transform.getX();
			int ty = transform.getY();
			int smx = (int)((mx - tx + lsx) / zs);
			int smy = (int)((my - ty + lsy) / zs);
			
			List<IGuiPanel> tmp = new ArrayList<IGuiPanel>(guiPanels);
			Collections.reverse(tmp);
			
			for(IGuiPanel panel : tmp)
			{
				used = panel.onMouseRelease(smx, smy, click);
				
				if(used)
				{
					break;
				}
			}
		}
		
		if(isDragging)
		{
			if(!Mouse.isButtonDown(0) && !Mouse.isButtonDown(2))
			{
				isDragging = false;
			}
			
			return true;
		}
		
		return used;
	}
	
	@Override
	public boolean onMouseScroll(int mx, int my, int scroll)
	{
		if(scroll == 0 || !transform.contains(mx, my))
		{
			return false;
		}
		
		float zs = zoomScale.readValue();
		int tx = transform.getX();
		int ty = transform.getY();
		int smx = (int)((mx - tx + lsx) / zs);
		int smy = (int)((my - ty + lsy) / zs);
		
		List<IGuiPanel> tmp = new ArrayList<IGuiPanel>(guiPanels);
		Collections.reverse(tmp);
		boolean used = false;
		
		for(IGuiPanel panel : tmp)
		{
			used = panel.onMouseScroll(smx, smy, scroll);
			
			if(used)
			{
				break;
			}
		}
		
		if(!used)
		{
			if(zoomMode)
			{
				float dy = -scroll * 0.05F;// * scrollSpeed;
				float cs = zoomScale.readValue();
				
				zoomScale.writeValue(cs + dy);
				
				this.refreshScrollBounds();
			} else if(scrollBounds.getHeight() > 0)
			{
				float dy = (scroll * scrollSpeed) / (float)scrollBounds.getHeight();
				float cs = scrollY.readValue();
				
				if(!((dy < 0F && cs <= 0F) || (dy > 0F && cs >= 1F)))
				{
					scrollY.writeValue(cs + dy);
					this.updatePanelScroll();
				}
			}
		}
		
		return used;
	}
	
	@Override
	public boolean onKeyTyped(char c, int keycode)
	{
		List<IGuiPanel> tmp = new ArrayList<IGuiPanel>(guiPanels);
		boolean used = false;
		
		for(IGuiPanel entry : tmp)
		{
			if(entry.onKeyTyped(c, keycode))
			{
				used = true;
				break;
			}
		}
		
		return used;
	}
	
	@Override
	public List<String> getTooltip(int mx, int my)
	{
		float zs = zoomScale.readValue();
		int tx = transform.getX();
		int ty = transform.getY();
		int smx = (int)((mx - tx + lsx) / zs);
		int smy = (int)((my - ty + lsy) / zs);
		
		List<IGuiPanel> tmp = new ArrayList<IGuiPanel>(guiPanels);
		Collections.reverse(tmp);
		
		for(IGuiPanel entry : tmp)
		{
			List<String> tt = entry.getTooltip(smx, smy);
			
			if(tt != null && tt.size() > 0)
			{
				return tt;
			}
		}
		
		return new ArrayList<String>();
	}
	
	@Override
	public void addPanel(IGuiPanel panel)
	{
		if(panel == null || guiPanels.contains(panel))
		{
			return;
		}
		
		guiPanels.add(panel);
		Collections.sort(guiPanels, ComparatorGuiDepth.INSTANCE);
		panel.initPanel();
		
		this.refreshScrollBounds();
	}
	
	@Override
	public boolean removePanel(IGuiPanel panel)
	{
		boolean b = guiPanels.remove(panel);
		
		if(b)
		{
			this.refreshScrollBounds();
		}
		
		return b;
	}
	
	private void refreshScrollBounds()
	{
		boolean first = true;
		int left = 0;
		int right = 0;
		int top = 0;
		int bottom = 0;
		
		List<IGuiPanel> tmp = new ArrayList<IGuiPanel>(guiPanels);
		float zs = zoomScale.readValue();

		for(IGuiPanel panel : tmp)
		{
			if(first)
			{
				left = panel.getTransform().getX();
				top = panel.getTransform().getY();
				right = panel.getTransform().getX() + panel.getTransform().getWidth();
				bottom = panel.getTransform().getY() + panel.getTransform().getHeight();
				first = false;
			} else
			{
				left = Math.min(left, panel.getTransform().getX());
				top = Math.min(top, panel.getTransform().getY());
				right = Math.max(right, panel.getTransform().getX() + panel.getTransform().getWidth());
				bottom = Math.max(bottom, panel.getTransform().getY() + panel.getTransform().getHeight());
			}
		}
		
		left -= margin;
		right += margin;
		top -= margin;
		bottom += margin;
		
		right -= (int)Math.ceil(this.getTransform().getWidth() / zs);
		bottom -= (int)Math.ceil(this.getTransform().getHeight() / zs);
		
		if(extendedScroll)
		{
			scrollBounds.x = Math.min(left, right);
			scrollBounds.y = Math.min(top, bottom);
			scrollBounds.w = Math.max(left, right) - scrollBounds.x;
			scrollBounds.h = Math.max(top, bottom) - scrollBounds.y;
		} else
		{
			scrollBounds.x = left;
			scrollBounds.y = right;
			scrollBounds.w = Math.max(0, right - left);
			scrollBounds.h = Math.max(0, bottom - top);
		}
		
		updatePanelScroll();
	}
	
	private void updatePanelScroll()
	{
		lsx = this.getScrollX();
		lsy = this.getScrollY();
	}
	
	@Override
	public List<IGuiPanel> getAllPanels()
	{
		return guiPanels;
	}
}
