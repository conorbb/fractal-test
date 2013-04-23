/*
 * Copyright 2012 Conor Byrne.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Based on code by Josef Jelinek
 * http://java.rubikscube.info/
 */

/*
 * 
 * STORING IN STACKED INT *
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
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


public class FractalView extends SurfaceView implements SurfaceHolder.Callback{

	private FVThread fvThread;



	class FVThread extends Thread {
		private int maxCount = 192; // maximum number of iterations
		private boolean smooth = false; // smoothing state
		private boolean antialias = false; // antialias state
		private int[][] colors; // palettes
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
		
		private double moveX, moveY;

		//GUI related variables
		FractalView mFView;
		private Bitmap zoomInBitmap;
		private Bitmap zoomOutBitmap;
		private Bitmap changeColorsBitmap;
		//private boolean fullDraw;
		private boolean circleToDraw = false;
		private float circlePositionX, circlePositionY;
		private Context mContext;
		private SurfaceHolder mSurfaceHolder;
		private Rect drawArea;

		//draw thread related
		private boolean running;
		private boolean fullDrawNeeded;
		private boolean partialDrawNeeded;
		private static final boolean DEBUG_TEXT = true;
		
		


		private int width, height; // current screen width and height Populate in constructor?




		private  final int[][][] COLOR_PALETTE = { // palette colors
				{ {12, 0, 10, 20}, {12, 50, 100, 240}, {12, 20, 3, 26}, {12, 230, 60, 20},
					{12, 25, 10, 9}, {12, 230, 170, 0}, {12, 20, 40, 10}, {12, 0, 100, 0},
					{12, 5, 10, 10}, {12, 210, 70, 30}, {12, 90, 0, 50}, {12, 180, 90, 120},
					{12, 0, 20, 40}, {12, 30, 70, 200} },
					{ {10, 70, 0, 20}, {10, 100, 0, 100}, {14, 255, 0, 0}, {10, 255, 200, 0} },
					{ {8, 40, 70, 10}, {9, 40, 170, 10}, {6, 100, 255, 70}, {8, 255, 255, 255} },
					{ {12, 0, 0, 64}, {12, 0, 0, 255}, {10, 0, 255, 255}, {12, 128, 255, 255}, {14, 64, 128, 255} },
					{ {16, 0, 0, 0}, {32, 255, 255, 255} },
		};

		private final int[][] ROWS = {
				{0, 16, 8}, {8, 16, 8}, {4, 16, 4}, {12, 16, 4},
				{2, 16, 2}, {10, 16, 2}, {6, 16, 2}, {14, 16, 2},
				{1, 16, 1}, {9, 16, 1}, {5, 16, 1}, {13, 16, 1},
				{3, 16, 1}, {11, 16, 1}, {7, 16, 1}, {15, 16, 1},
		};

		public void doSetup() {
			Resources res = mContext.getResources();
			drawArea = null;
			zoomInBitmap = BitmapFactory.decodeResource(res,R.drawable.in);
			zoomOutBitmap = BitmapFactory.decodeResource(res,R.drawable.out);
			changeColorsBitmap = BitmapFactory.decodeResource(res,R.drawable.colors); 
			numDraws = 0;
			mPaint = new Paint();
			moveX = moveY = 0;
			// initialize color palates

			colors = new int[COLOR_PALETTE.length][];							

			for (int p = 0; p < COLOR_PALETTE.length; p++) { // process all palettes 
				int n = 0;
				for (int i = 0; i < COLOR_PALETTE[p].length; i++) // get the number of all colors
					n += COLOR_PALETTE[p][i][0];
				colors[p] = new int[n]; // allocate pallete 					
				n = 0;
				for (int i = 0; i < COLOR_PALETTE[p].length; i++) { // interpolate all colors
					int[] c1 = COLOR_PALETTE[p][i]; // first referential color
					int[] c2 = COLOR_PALETTE[p][(i + 1) % COLOR_PALETTE[p].length]; // second ref. color
					for (int j = 0; j < c1[0]; j++) // linear interpolation of RGB values
						colors[p][n + j] = getColorAsInt(								
								(c1[1] * (c1[0] - 1 - j) + c2[1] * j) / (c1[0] - 1),
								(c1[2] * (c1[0] - 1 - j) + c2[2] * j) / (c1[0] - 1),
								(c1[3] * (c1[0] - 1 - j) + c2[3] * j) / (c1[0] - 1));
					n += c1[0];
				}
				setDrawingCacheEnabled(true);
				//fullDraw = true;
				fullDrawNeeded = true;
			}





		}

		private int getColorAsInt(int red, int green, int blue){

			return (255 << 24) | (red << 16) | (green << 8) | blue;
		}

		private void drawCircle(float x, float y){
			circleToDraw = true;
			circlePositionX = x;
			circlePositionY = y;
			fullDrawNeeded = true;
		}
		
		private void setCenter(int x, int y){
			int centerX, centerY;
			

			
			//Get the center
			centerX = drawArea.right/2;
			centerY = drawArea.bottom/2;
			
			if(x > centerX){
				moveX = (((double)x / drawArea.right) * 50)/100;
			}
			else{
				moveX = (((double)Math.abs(x-centerX)/centerX)*50)/-100;
			}
			
			if(y > centerY){
				moveY = (((double)y / drawArea.bottom) * 50)/100;
			}
			else{
				moveY = (((double)Math.abs(y-centerY) / centerY) * 50)/-100;
			}
			
			viewX += moveX *zoom;
			viewY += moveY*zoom;
			fullDrawNeeded = true;
				
		}
		
		private  int getColor(int i) {
			int palSize = colors[pal].length;
			return colors[pal][(i + palSize) % palSize];
		}

		private void nextPalette() {
			pal = (pal + 1) % colors.length;
			fullDrawNeeded = true;
		}

		private void zoomOut(){
			viewX -= 0.5 * zoom;
			viewY -= 0.5 * zoom;
			zoom *= 2.0;

			//viewY=0;
			fullDrawNeeded = true;

		}

		private void zoomIn(){
			
			viewX += 0.25 * zoom;
			viewY += 0.25 * zoom;
			zoom *= 0.5;

			//viewY += 0.1;
			fullDrawNeeded = true;

		}


		//TODO stacked int here
		private int genColor(double x, double y) {
			int count = julia ? mandel(x, y, juliaX, juliaY): mandel(0.0, 0.0, x, y);
			int color = getColor(count / 256);
			if (smooth) {
				int color2 = getColor(count / 256 - 1);
				int k1 = count % 256;
				int k2 = 255 - k1;
				
				/*int red = (k1 * color.getRed() + k2 * color2.getRed()) / 255;
				int green = (k1 * color.getGreen() + k2 * color2.getGreen()) / 255;
				int blue = (k1 * color.getBlue() + k2 * color2.getBlue()) / 255;*/
				
				int red = ((k1 * (color & 0x00FF0000)>>16) + (k2 * (color2& 0x00FF0000)>>16)) / 255;
				int green = ((k1 * (color & 0x0000FF00)>>8) + (k2 * (color2& 0x0000FF00)>>8)) / 255;
				int blue = ((k1 * (color & 0x000000FF)) + (k2 * (color2& 0x000000FF))) / 255;
				color = getColorAsInt(red, green, blue);
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




		public FVThread(SurfaceHolder surfaceHolder, Context context, Handler handler) {
			mSurfaceHolder = surfaceHolder;
			mContext=context;

			doSetup();
		}

		public void setRunning(boolean b){
			running = b;
		}

		public void run() {
			Log.v("FV Thread","Thread going!");



			while (running) {
				Canvas c = null;


				if(fullDrawNeeded){

					//Low quality fractal.
					try {
						c = mSurfaceHolder.lockCanvas();

						synchronized (mSurfaceHolder) {
							// Draw method

							drawPreviewFractal(c);



						}
					}
					catch(Exception e){
						e.printStackTrace();
					}
					finally {
						// do this in a finally so that if an exception is thrown
						// during the above, we don't leave the Surface in an
						// inconsistent state
						if (c != null) {
							mSurfaceHolder.unlockCanvasAndPost(c);
						}
					}

					//High Quality draw
					try {
						c = mSurfaceHolder.lockCanvas();

						synchronized (mSurfaceHolder) {
							// Draw method


							drawFullFractal(c);


						}
					}
					catch(Exception e){
						e.printStackTrace();
					}
					finally {
						// do this in a finally so that if an exception is thrown
						// during the above, we don't leave the Surface in an
						// inconsistent state
						if (c != null) {
							mSurfaceHolder.unlockCanvasAndPost(c);
						}
					}


				}
			}


		}


		private void drawPreviewFractal(Canvas canvas){
			if (drawArea.width() != width || drawArea.height() != height) {
				width = drawArea.width();
				height = drawArea.height();
			}
			double r = zoom / Math.min(width, height);
			double sx = width > height ? 2.0 * r * (width - height) : 0.0;
			double sy = height > width ? 2.0 * r * (height - width) : 0.0;
			for (int y = 0; y < height + 4; y += 8) {
				for (int x = 0; x < width + 4; x += 8) {
					double dx = 4.0 * (x * r + viewX) - 2.0 - sx;
					double dy = -4.0 * (y * r + viewY) + 2.0 + sy;
					int color = genColor(dx, dy);	
					mPaint.setColor(color);  ///////////////

					canvas.drawRect(x - 4, y - 4, x - 4 +8 , y -4 +8, mPaint);
				}
			}


		}



		private void drawFullFractal(Canvas canvas) {
			//Debug.startMethodTracing("Fractal Test");
			long itterationCount = 0;


			if (drawArea.width() != width || drawArea.height() != height) {
				width = drawArea.width();
				height = drawArea.height();
			}
			long startTime = System.currentTimeMillis();

			//Center dot
			//mPaint.setColor(Color.RED);
			//canvas.drawCircle(drawArea.right/2, drawArea.bottom/2, 4, mPaint);


			double r = zoom / Math.min(width, height);
			double sx = width > height ? 2.0 * r * (width - height) : 0.0;
			double sy = height > width ? 2.0 * r * (height - width) : 0.0;


			//fractal image drawing
			for (int row = 0; row < ROWS.length; row++) {
				for (int y = ROWS[row][0]; y < height; y += ROWS[row][1]) {

					for (int x = 0; x < width; x++) {
						double dx = 4.0 * (x * r + viewX) - 2.0 - sx;
						double dy = -4.0 * (y * r + viewY) + 2.0 + sy;
						int color = genColor(dx, dy);						
						// computation of average color for anti aliasing
						if (antialias) {
							int c1 = genColor(dx - 0.25 * r, dy - 0.25 * r);
							int c2 = genColor(dx + 0.25 * r, dy - 0.25 * r);
							int c3 = genColor(dx + 0.25 * r, dy + 0.25 * r);
							int c4 = genColor(dx - 0.25 * r, dy + 0.25 * r);
							int red = (((color & 0x00FF0000)>>16) + ((c1& 0x00FF0000)>>16) + ((c2& 0x00FF0000)>>16) + 
									((c3& 0x00FF0000)>>16) + ((c4& 0x00FF0000)>>16)) / 5;
							int green = ( ((color & 0x0000FF00)>>8) + (c1 & 0x0000FF00)>>8) + ((c2& 0x0000FF00)>>8) +
									((c3& 0x0000FF00)>>8) + ((c4& 0x0000FF00)>>8) / 5;
							int blue = (color& 0x000000FF + c1& 0x000000FF + c2& 0x000000FF + 
									c3& 0x000000FF + c4& 0x000000FF) / 5;
							color = (255 << 24) | (red << 16) | (green << 8) | blue;
						}
						itterationCount++;
						mPaint.setColor(color);						
						canvas.drawRect(x, y - ROWS[row][2] / 2, x+1,(y - ROWS[row][2] / 2) + (ROWS[row][2]),mPaint); 
					}
				}
			}

			if(circleToDraw){
				mPaint.setColor(Color.WHITE);
				canvas.drawCircle(circlePositionX, circlePositionY, 5, mPaint);

			}
			numDraws++;
			setDrawingCacheEnabled(true);
			canvas.drawBitmap(zoomInBitmap, null, new Rect(drawArea.right-160, drawArea.bottom -80, drawArea.right-80, drawArea.bottom), mPaint);
			canvas.drawBitmap(zoomOutBitmap, null, new Rect(drawArea.right-80, drawArea.bottom -80, drawArea.right, drawArea.bottom), mPaint);
			canvas.drawBitmap(changeColorsBitmap, null, new Rect(drawArea.right-240, drawArea.bottom -80, drawArea.right-160, drawArea.bottom), mPaint);
			mPaint.setColor(Color.WHITE);
			if(DEBUG_TEXT){
				canvas.drawText("ViewX: " + viewX +" ViewY: "+ viewY , 15f, 15f, mPaint);
				canvas.drawText("Zoom: " + zoom , 15f, 30f, mPaint);
				
				canvas.drawText("Num Draws: " + numDraws , 15f, 45f, mPaint);
				canvas.drawText("Move to X: " + moveX+" Y: "+ moveY, 15f, 60f, mPaint);
			}

			//Center dot
			mPaint.setColor(Color.RED);
			canvas.drawCircle(drawArea.right/2, drawArea.bottom/2, 4, mPaint);

			fullDrawNeeded = false;
			//Debug.stopMethodTracing();

		}

		public void setViewDrawArea(Rect r){
			drawArea = r;
		}

		private void doTouchEvent(float eventX,float eventY){

			synchronized (mSurfaceHolder) {

				//mSurfaceHolder.getSurface().

				int x,y;
				//Rect r = new Rect();

				x = Math.round(eventX);
				y = Math.round(eventY);



				if( new Rect(drawArea.right-160, drawArea.bottom -80, drawArea.right-80, drawArea.bottom).contains(x, y)){
					zoomIn();
				}
				else if(new Rect(drawArea.right-80, drawArea.bottom -80, drawArea.right, drawArea.bottom).contains(x, y)){
					zoomOut();
				}
				else if(new Rect(drawArea.right-240, drawArea.bottom -80, drawArea.right-160, drawArea.bottom).contains(x, y)){
					nextPalette();

				}
				else{
					
					setCenter(x, y);
					drawCircle(x, y);
				}

			}


		}	
	}

	//TODO stacked int here
	// Inner class for colors
	/*	class MyColor{

		//      STACKED INT FORMAT for color
		// * 	int alpha=argb>>24;					OR int alpha=argb>>24;	
		//    *	int red=(argb-alpha)>>16;			OR int red=(argb & 0x00FF0000)>>16;
		//    *	int green=(argb-(alpha+red))>>8;	OR int green=(argb & 0x0000FF00)>>8;
		//    *	int blue=(argb-(alpha+red+green));  OR int blue=(argb & 0x000000FF);

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
	}*/









	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

		// TODO Do a redraw
		fvThread.setViewDrawArea(new Rect(this.getLeft(), this.getTop(), this.getRight(), this.getBottom()));

	}

	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		fvThread.setViewDrawArea(new Rect(this.getLeft(), this.getTop(), this.getRight(), this.getBottom()));
		fvThread.setRunning(true);
		fvThread.start();

	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		boolean retry = true;

		fvThread.setRunning(false);
		Log.v("FView", "Trying thread.join()");

		while (retry) {
			try {

				fvThread.join();
				retry = false;
			} catch (InterruptedException e) {

			}
		}

	}

	public FractalView(Context context, AttributeSet attrs) {
		super(context, attrs);
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);		
		setupThread(context,holder);
		// TODO Auto-generated constructor stub

	}

	public FractalView(Context context, AttributeSet attrs,int defStyle){
		super(context,attrs,defStyle);
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);		
		setupThread(context,holder);

	}

	private void  setupThread(Context context, SurfaceHolder holder){


		Log.v("Fractal View"," Setting up thread!");

		// create thread only; it's started in surfaceCreated()
		//TODO pass null handler for the Minute, find out what it does later!
		fvThread = new FVThread(holder, context, new Handler() {
			@Override
			public void handleMessage(Message m) {
				//mStatusText.setVisibility(m.getData().getInt("viz"));
				//mStatusText.setText(m.getData().getString("text"));
			}
		});
		setFocusable(true);


	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// TODO Auto-generated method stub
		//return super.onTouchEvent(event);


		fvThread.doTouchEvent(event.getX(),event.getY());

		return false;
	}



	/*
	 * 
	 //*    ON TOUCH METHOD FOR BUTTONS
	 * 		this.setOnTouchListener(new OnTouchListener() {

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
			});*/



}
