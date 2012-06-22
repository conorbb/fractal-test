/*
 * 
 * STRORING IN STACKED INT *
 * (alpha << 24) | (red << 16) | (green << 8) | blue
 *
 * RETREIVE FROM STACKED INT (FOR COLOR)
 * 	int alpha=argb>>24;					OR int alpha=argb>>24;	
 *	int red=(argb-alpha)>>16;			OR int red=(argb & 0x00FF0000)>>16;
 *	int green=(argb-(alpha+red))>>8;	OR int green=(argb & 0x0000FF00)>>8;
 *	int blue=(argb-(alpha+red+green));  OR int blue=(argb & 0x000000FF);
 * 
 * 
 */
package com.conorb.fractal.test;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
//import android.graphics.Color;
//import android.provider.Settings.System;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class FractalView extends View{

	private int maxCount = 192; // maximum number of iterations
	private boolean smooth = false; // smoothing state
	private boolean antialias = false; // antialias state
	//private boolean toDrag = false; // dragging state
	//private boolean rect = true; // zooming or moving mode for dragging
	private MyColor[][] colors; // palettes
	private int pal = 0; // current palette
	private Paint mPaint;
	private long numDraws;


	// julia set variables
	private boolean julia =false;
	private double juliaX, juliaY;
	// currently visible relative window dimensions
	private double viewX = 0.0;
	private double viewY = 0.0;
	private double zoom = 1.0;
	FractalView mFView;
	private Bitmap zoomInBitmap;
	private Bitmap zoomOutBitmap;
	private Bitmap changeColorsBitmap;
	private boolean fullDraw;
	private boolean circleToDraw = false;
	private float circlePositionX, circlePositionY;

	//TODO Find why I need Image type.
	//private Image image; // offscreen image for double buffering
	//TODO Find why I need Graphics.
	//private Graphics graphics; // offscreen graphics for the offscreen image
	private int width, height; // current screen width and height Populate in constructor?

	//private Thread thread = null;

	// private int mouseX, mouseY; // mouse position when the button was pressed
	//private int dragX, dragY; // current mouse position during dragging



	private static final int[][][] colpal = { // palette colors
		{ {12, 0, 10, 20}, {12, 50, 100, 240}, {12, 20, 3, 26}, {12, 230, 60, 20},
			{12, 25, 10, 9}, {12, 230, 170, 0}, {12, 20, 40, 10}, {12, 0, 100, 0},
			{12, 5, 10, 10}, {12, 210, 70, 30}, {12, 90, 0, 50}, {12, 180, 90, 120},
			{12, 0, 20, 40}, {12, 30, 70, 200} },
			{ {10, 70, 0, 20}, {10, 100, 0, 100}, {14, 255, 0, 0}, {10, 255, 200, 0} },
			{ {8, 40, 70, 10}, {9, 40, 170, 10}, {6, 100, 255, 70}, {8, 255, 255, 255} },
			{ {12, 0, 0, 64}, {12, 0, 0, 255}, {10, 0, 255, 255}, {12, 128, 255, 255}, {14, 64, 128, 255} },
			{ {16, 0, 0, 0}, {32, 255, 255, 255} },
	};

	public void doSetup() {
		Resources res = this.getContext().getResources();
		zoomInBitmap = BitmapFactory.decodeResource(res,R.drawable.in);
		zoomOutBitmap = BitmapFactory.decodeResource(res,R.drawable.out);
		changeColorsBitmap = BitmapFactory.decodeResource(res,R.drawable.colors); 
		numDraws = 0;
		mPaint = new Paint();
		// initialize color palates
		mFView=this;

		colors = new MyColor[colpal.length][];
		for (int p = 0; p < colpal.length; p++) { // process all palettes
			int n = 0;
			for (int i = 0; i < colpal[p].length; i++) // get the number of all colors
				n += colpal[p][i][0];
			colors[p] = new MyColor[n]; // allocate pallete
			n = 0;
			for (int i = 0; i < colpal[p].length; i++) { // interpolate all colors
				int[] c1 = colpal[p][i]; // first referential color
				int[] c2 = colpal[p][(i + 1) % colpal[p].length]; // second ref. color
				for (int j = 0; j < c1[0]; j++) // linear interpolation of RGB values
					colors[p][n + j] = new MyColor(
							(c1[1] * (c1[0] - 1 - j) + c2[1] * j) / (c1[0] - 1),
							(c1[2] * (c1[0] - 1 - j) + c2[2] * j) / (c1[0] - 1),
							(c1[3] * (c1[0] - 1 - j) + c2[3] * j) / (c1[0] - 1));
				n += c1[0];
			}
			setDrawingCacheEnabled(true);
			fullDraw = true;
		}
		
		
		this.setOnTouchListener(new OnTouchListener() {
			
			public boolean onTouch(View v, MotionEvent event) {
				int x,y;
				Rect r = new Rect();
				
				x = Math.round(event.getX());
				y = Math.round(event.getY());
				
				if( new Rect(v.getRight()-160, v.getBottom() -80, v.getRight()-80, v.getBottom()).contains(x, y)){
					zoomIn();
				}
				else if(new Rect(v.getRight()-80, v.getBottom() -80, v.getRight(), v.getBottom()).contains(x, y)){
					zoomOut();
				}
				else if(new Rect(v.getRight()-240, v.getBottom() -80, v.getRight()-160, v.getBottom()).contains(x, y)){
					nextPalette();
					
				}
				else{
					drawCircle(event.getX(), event.getY());
				}
				
				return false;
			}
		});
		

	}
	
	private void drawCircle(float x, float y){
		circleToDraw = true;
		circlePositionX = x;
		circlePositionY = y;
		
		invalidate();
	}

	private  MyColor getColor(int i) {
		int palSize = colors[pal].length;
		return colors[pal][(i + palSize) % palSize];
	}

	private void nextPalette() {
		pal = (pal + 1) % colors.length;
		fullDraw = true;
		invalidate();
	}
	
	private void zoomOut(){
	      viewX -= 0.5 * zoom;
	      viewY -= 0.5 * zoom;
	      zoom *= 2.0;
	      fullDraw = true;
	      invalidate(); 
	}
	
	private void zoomIn(){
	      viewX += 0.25 * zoom;
	      viewY += 0.25 * zoom;
	      zoom *= 0.5;
	      fullDraw = true;
	      invalidate(); 
	}

	private static final int[][] rows = {
		{0, 16, 8}, {8, 16, 8}, {4, 16, 4}, {12, 16, 4},
		{2, 16, 2}, {10, 16, 2}, {6, 16, 2}, {14, 16, 2},
		{1, 16, 1}, {9, 16, 1}, {5, 16, 1}, {13, 16, 1},
		{3, 16, 1}, {11, 16, 1}, {7, 16, 1}, {15, 16, 1},
	};

	private MyColor genColor(double x, double y) {
		int count = julia ? mandel(x, y, juliaX, juliaY): mandel(0.0, 0.0, x, y);
		MyColor color = getColor(count / 256);
		if (smooth) {
			MyColor color2 = getColor(count / 256 - 1);
			int k1 = count % 256;
			int k2 = 255 - k1;
			int red = (k1 * color.getRed() + k2 * color2.getRed()) / 255;
			int green = (k1 * color.getGreen() + k2 * color2.getGreen()) / 255;
			int blue = (k1 * color.getBlue() + k2 * color2.getBlue()) / 255;
			color = new MyColor(red, green, blue);
		}
		return color;
	}

	private int mandel(double zRe, double zIm, double cRe, double cIm) {
		double zRe2 = zRe * zRe;
		double zIm2 = zIm * zIm;
		double zM2 = 0.0;
		int count = 0;
		while (zRe2 + zIm2 < 4.0 && count < maxCount) {
			zM2 = zRe2 + zIm2;
			zIm = 2.0 * zRe * zIm + cIm;
			zRe = zRe2 - zIm2 + cRe;
			zRe2 = zRe * zRe;
			zIm2 = zIm * zIm;
			count++;
		}
		if (count == 0 || count == maxCount)
			return 0;
		// transition smoothing
		zM2 += 0.000000001;
		return 256 * count + (int)(255.0 * Math.log(4.0 / zM2) / Math.log((zRe2 + zIm2) / zM2));
	}




	public FractalView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
		doSetup();
	}
	
	public FractalView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
		doSetup();
	}
	
	

	@Override
	protected void onDraw(Canvas canvas) {
		
		super.onDraw(canvas);
		Rect rect = canvas.getClipBounds();
		if (rect.width() != width || rect.height() != height) {
			width = rect.width();
			height = rect.height();
		}
		long startTime = System.currentTimeMillis();
			
		if(fullDraw){
			
		
		

		//Dimension size = getSize();


		// TODO REDUNDANT: create off screen buffer for double buffering

		


		// fractal image pre-drawing
		double r = zoom / Math.min(width, height);
		double sx = width > height ? 2.0 * r * (width - height) : 0.0;
		double sy = height > width ? 2.0 * r * (height - width) : 0.0;
		for (int y = 0; y < height + 4; y += 8) {
			//    if (Thread.interrupted())
			//    return true;
			for (int x = 0; x < width + 4; x += 8) {
				double dx = 4.0 * (x * r + viewX) - 2.0 - sx;
				double dy = -4.0 * (y * r + viewY) + 2.0 + sy;
				MyColor color = genColor(dx, dy);
				mPaint.setColor(color.getColor());  ///////////////
				//graphics.fillRect(x - 4, y - 4, 8, 8);  // x, y, w, h
				canvas.drawRect(x - 4, y - 4, x - 4 +8 , y -4 +8, mPaint);
			}
		}
		//repaint();
		// fractal image drawing
		for (int row = 0; row < rows.length; row++) {
			for (int y = rows[row][0]; y < height; y += rows[row][1]) {
				// if (Thread.interrupted())
				//  return true;
				for (int x = 0; x < width; x++) {
					double dx = 4.0 * (x * r + viewX) - 2.0 - sx;
					double dy = -4.0 * (y * r + viewY) + 2.0 + sy;
					MyColor color = genColor(dx, dy);
					// computation of average color for anti aliasing
					if (antialias) {
						MyColor c1 = genColor(dx - 0.25 * r, dy - 0.25 * r);
						MyColor c2 = genColor(dx + 0.25 * r, dy - 0.25 * r);
						MyColor c3 = genColor(dx + 0.25 * r, dy + 0.25 * r);
						MyColor c4 = genColor(dx - 0.25 * r, dy + 0.25 * r);
						int red = (color.getRed() + c1.getRed() + c2.getRed() + c3.getRed() + c4.getRed()) / 5;
						int green = (color.getGreen() + c1.getGreen() + c2.getGreen() + c3.getGreen() + c4.getGreen()) / 5;
						int blue = (color.getBlue() + c1.getBlue() + c2.getBlue() + c3.getBlue() + c4.getBlue()) / 5;
						color = new MyColor(red, green, blue);
					}
					mPaint.setColor(color.getColor());		//paint object
					canvas.drawRect(x, y - rows[row][2] / 2, x+1,(y - rows[row][2] / 2) + (rows[row][2]),mPaint); // TODO check x+1 is ok..
				}
			}
		}
		
		if(circleToDraw){
			mPaint.setColor(Color.WHITE);
			canvas.drawCircle(circlePositionX, circlePositionY, 20, mPaint);
			
		}
		numDraws++;
		setDrawingCacheEnabled(true);
		canvas.drawBitmap(zoomInBitmap, null, new Rect(rect.right-160, rect.bottom -80, rect.right-80, rect.bottom), mPaint);
		canvas.drawBitmap(zoomOutBitmap, null, new Rect(rect.right-80, rect.bottom -80, rect.right, rect.bottom), mPaint);
		canvas.drawBitmap(changeColorsBitmap, null, new Rect(rect.right-240, rect.bottom -80, rect.right-160, rect.bottom), mPaint);
		mPaint.setColor(Color.WHITE);
		canvas.drawText("Num Draws: " + numDraws , 15f, 15f, mPaint);
		canvas.drawText("Major Draw time: " + (System.currentTimeMillis()-startTime) + "ms", 15f, 30f, mPaint);
		
		
			fullDraw = false;
		}
		else{
			
			Bitmap b = getDrawingCache();
			if(b!=null){
				canvas.drawBitmap(b, 0, 0, mPaint);
			}

		}
		
		if(circleToDraw){
			mPaint.setColor(Color.WHITE);
			canvas.drawCircle(circlePositionX, circlePositionY, 20, mPaint);
			
		}
		


		
	}

	// * 	int alpha=argb>>24;					OR int alpha=argb>>24;	
	//    *	int red=(argb-alpha)>>16;			OR int red=(argb & 0x00FF0000)>>16;
	//    *	int green=(argb-(alpha+red))>>8;	OR int green=(argb & 0x0000FF00)>>8;
	//    *	int blue=(argb-(alpha+red+green));  OR int blue=(argb & 0x000000FF);



	// Inner class for colors
	class MyColor{
		private int colorAsInt;

		public MyColor(int red, int green, int blue){

			colorAsInt = (255 << 24) | (red << 16) | (green << 8) | blue;

		}

		public int getRed(){
			return (colorAsInt & 0x00FF0000)>>16;
		}

		public int getGreen(){
			return (colorAsInt & 0x0000FF00)>>8;
		}

		public int getBlue(){
			return (colorAsInt & 0x000000FF);
		}

		public int getColor(){
			return colorAsInt;
		}
	}







}
