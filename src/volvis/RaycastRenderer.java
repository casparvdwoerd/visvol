/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volvis;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import gui.RaycastRendererPanel;
import gui.TransferFunction2DEditor;
import gui.TransferFunctionEditor;
import java.awt.image.BufferedImage;
import util.TFChangeListener;
import util.VectorMath;
import volume.GradientVolume;
import volume.Volume;
import volume.VoxelGradient;

/**
 *
 * @author michel
 */
public class RaycastRenderer extends Renderer implements TFChangeListener {

    private Volume volume = null;
    private GradientVolume gradients = null;
    RaycastRendererPanel panel;
    TransferFunction tFunc;
    TransferFunctionEditor tfEditor;
    TransferFunction2DEditor tfEditor2D;
    
    private int option = 0;
    
    
    public RaycastRenderer() {
        panel = new RaycastRendererPanel(this);
        panel.setSpeedLabel("0");
    }

    public void setVolume(Volume vol) {
        System.out.println("Assigning volume");
        volume = vol;

        System.out.println("Computing gradients");
        gradients = new GradientVolume(vol);

        // set up image for storing the resulting rendering
        // the image width and height are equal to the length of the volume diagonal
        int imageSize = (int) Math.floor(Math.sqrt(vol.getDimX() * vol.getDimX() + vol.getDimY() * vol.getDimY()
                + vol.getDimZ() * vol.getDimZ()));
        if (imageSize % 2 != 0) {
            imageSize = imageSize + 1;
        }
        image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        // create a standard TF where lowest intensity maps to black, the highest to white, and opacity increases
        // linearly from 0.0 to 1.0 over the intensity range
        tFunc = new TransferFunction(volume.getMinimum(), volume.getMaximum());
        
        // uncomment this to initialize the TF with good starting values for the orange dataset 
        tFunc.setTestFunc();
        
        
        tFunc.addTFChangeListener(this);
        tfEditor = new TransferFunctionEditor(tFunc, volume.getHistogram());
        
        tfEditor2D = new TransferFunction2DEditor(volume, gradients);
        tfEditor2D.addTFChangeListener(this);

        System.out.println("Finished initialization of RaycastRenderer");
    }

    public RaycastRendererPanel getPanel() {
        return panel;
    }

    public TransferFunction2DEditor getTF2DPanel() {
        return tfEditor2D;
    }
    
    public TransferFunctionEditor getTFPanel() {
        return tfEditor;
    }
     

    short getVoxel(double[] coord, boolean use_TLIP) {
        
        if(use_TLIP){
            short voxval = TLIP(coord);
            return voxval;
        }

        if (coord[0] < 0 || coord[0] >= volume.getDimX() || coord[1] < 0 || coord[1] >= volume.getDimY()
                || coord[2] < 0 || coord[2] >= volume.getDimZ()) {
            return 0;
        }

        int x = (int) Math.floor(coord[0]);
        int y = (int) Math.floor(coord[1]);
        int z = (int) Math.floor(coord[2]);

        return volume.getVoxel(x, y, z);
    }
    
    short TLIP(double[] coord) {
        
        if (coord[0] < 0 || coord[0] +1 >= volume.getDimX() || coord[1] < 0 || coord[1] +1 >= volume.getDimY()
                || coord[2] < 0 || coord[2] +1 >= volume.getDimZ()) {
            return 0;
        }
        
        int x0 = (int) Math.floor(coord[0]);
        int y0 = (int) Math.floor(coord[1]);
        int z0 = (int) Math.floor(coord[2]);
        
        int x1 = (int) Math.ceil(coord[0]);
        int y1 = (int) Math.ceil(coord[1]);
        int z1 = (int) Math.ceil(coord[2]);
        
        double a = coord[0] - x0;
        double b = coord[1] - y0;
        double c = coord[2] - z0;
        
        double p000 = volume.getVoxel(x0, y0, z0);
        double p100 = volume.getVoxel(x1, y0, z0);
        double p101 = volume.getVoxel(x1, y0, z1);
        double p110 = volume.getVoxel(x1, y1, z0);
        double p111 = volume.getVoxel(x1, y1, z1);
        double p001 = volume.getVoxel(x0, y0, z1);
        double p010 = volume.getVoxel(x0, y1, z0);
        double p011 = volume.getVoxel(x0, y1, z1);
        
        double val= p000 * (1-a)*(1-b)*(1-c) + p100 * a*(1-b)*(1-c) + p110 * a*b*(1-c) + p010 * (1-a)*b*(1-c) + p001 * (1-a)*(1-b)*c + p101 * a*(1-b)*c + p111 * a*b*c + p011 * (1-a)*b*c;
        
        return (short) val;
        
    }
    
        void slicer(double[] viewMatrix) {
        boolean use_TLIP = true;
        
        if(interactiveMode){
            use_TLIP = false;
        }

        // clear image SETS IMAGE CONTENT TO 0 FOR ALL PIXELS
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        // THIS IS THE PLANE THAT DEFINES THE SLICE
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        // (IMAGECENTER,IMAGECENTER) IS THE CENTER OF THE IMAGE
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                        + volumeCenter[0];
                pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                        + volumeCenter[1];
                pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                        + volumeCenter[2];

                int val = getVoxel(pixelCoord, use_TLIP);
                
                // Map the intensity to a grey value by linear scaling
                voxelColor.r = val/max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                // Alternatively, apply the transfer function to obtain a color
                //voxelColor = tFunc.getColor(val);
                
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }

    }
    
    void compositing(double[] viewMatrix, boolean TLIP) {
        
        //efficiency variables
        int ray_steps = 1;
        int image_steps=1;
        if(interactiveMode){
            TLIP = false;
            ray_steps = 3;
            image_steps = 3;
        }

        // clear image SETS IMAGE CONTENT TO 0 FOR ALL PIXELS
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        // THIS IS THE PLANE THAT DEFINES THE SLICE
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        // (IMAGECENTER,IMAGECENTER) IS THE CENTER OF THE IMAGE
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();
        int maxDepth = (int) Math.floor(Math.sqrt( Math.pow(volume.getDimX(), 2) + Math.pow(volume.getDimY(), 2) + Math.pow(volume.getDimZ(), 2)));

        
        for (int j = 0; j < image.getHeight(); j=j+image_steps) {
            for (int i = 0; i < image.getWidth(); i=i+image_steps) {
                
                //Create ci (colorOut) and ci-1 (colorIn)
                TFColor colorOut = new TFColor();
                TFColor colorIn = new TFColor();
                
                for (int ray = 0; ray < maxDepth; ray=ray+ray_steps){
                    
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                            + volumeCenter[0] + viewVec[0] * (ray-imageCenter);
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                            + volumeCenter[1] + viewVec[1] * (ray-imageCenter);
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                            + volumeCenter[2] + viewVec[2] * (ray-imageCenter) ;

                    int val = getVoxel(pixelCoord, TLIP);
                    
                    
                    voxelColor = tFunc.getColor(val);
                    
                    //Back to front, from the slides
                    colorOut.r = voxelColor.a*voxelColor.r + (1-voxelColor.a)*colorIn.r;
                    colorOut.g = voxelColor.a*voxelColor.g + (1-voxelColor.a)*colorIn.g;
                    colorOut.b = voxelColor.a*voxelColor.b + (1-voxelColor.a)*colorIn.b;
                    colorOut.a = (1-voxelColor.a)*colorIn.a;

                    colorIn = colorOut;       

                }
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                //Use colorOut instead of voxelColor
                int c_alpha = (1-colorOut.a) <= 1.0 ? (int) Math.floor((1-colorOut.a) * 255) : 255;
                int c_red = colorOut.r <= 1.0 ? (int) Math.floor(colorOut.r * 255) : 255;
                int c_green = colorOut.g <= 1.0 ? (int) Math.floor(colorOut.g * 255) : 255;
                int c_blue = colorOut.b <= 1.0 ? (int) Math.floor(colorOut.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
                
                //set skipped pixels to same value during interactive mode
                if(image_steps>1){
                    if(j+1<image.getHeight() && i+1<image.getWidth()){
                        image.setRGB(i, j+1, pixelColor);
                        image.setRGB(i+1, j, pixelColor);
                        image.setRGB(i+1, j+1, pixelColor);
                    }
                    if(j+2<image.getHeight() && i+2<image.getWidth()){
                        image.setRGB(i, j+2, pixelColor);
                        image.setRGB(i+1, j+2, pixelColor);
                        image.setRGB(i+2, j, pixelColor);
                        image.setRGB(i+2, j+1, pixelColor);
                        image.setRGB(i+2, j+2, pixelColor);
                    }
                }
            }
        }

    }
    
    void twoD(double[] viewMatrix, boolean TLIP) {
       
        //efficiency variables
        int ray_steps = 1;
        int image_steps=1;
        if(interactiveMode){
            TLIP = false;
            ray_steps = 3;
            image_steps = 3;
        }
        
        //Get the values from the widget
        int chosenIntensity = tfEditor2D.triangleWidget.baseIntensity;
        double chosenRadius = tfEditor2D.triangleWidget.radius;
        TFColor chosenColor = tfEditor2D.triangleWidget.color;
        

        // clear image SETS IMAGE CONTENT TO 0 FOR ALL PIXELS
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        // THIS IS THE PLANE THAT DEFINES THE SLICE
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        // (IMAGECENTER,IMAGECENTER) IS THE CENTER OF THE IMAGE
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();
        int maxDepth = (int) Math.floor(Math.sqrt( Math.pow(volume.getDimX(), 2) + Math.pow(volume.getDimY(), 2) + Math.pow(volume.getDimZ(), 2)));

        
        for (int j = 0; j < image.getHeight(); j=j+image_steps) {
            for (int i = 0; i < image.getWidth(); i=i+image_steps) {
                
                //Create ci (colorOut) and ci-1 (colorIn)
                TFColor colorOut = new TFColor();
                TFColor colorIn = new TFColor();
                
                for (int ray = 0; ray < maxDepth; ray = ray + ray_steps){
                    
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                            + volumeCenter[0] + viewVec[0] * (ray-imageCenter);
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                            + volumeCenter[1] + viewVec[1] * (ray-imageCenter);
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                            + volumeCenter[2] + viewVec[2] * (ray-imageCenter) ;
                    
                                                        
                    int val = getVoxel(pixelCoord, TLIP);
                    
                    if ( ( (pixelCoord[0] < volume.getDimX() -1 && pixelCoord[0] >= 0) || (pixelCoord[1] < volume.getDimY() -1 && pixelCoord[1]  >= 0) || (pixelCoord[2] < volume.getDimZ() -1 && pixelCoord[2] >= 0) ) && val > 0) {

                        VoxelGradient vg = gradients.getGradient((int)Math.floor(pixelCoord[0]), (int)Math.floor(pixelCoord[1]), (int)Math.floor(pixelCoord[2]));

                        //Levoy's method
                        if (Math.abs(vg.mag) == 0 && val == chosenIntensity) {
                            voxelColor.a = chosenColor.a * 1.0;
                        } else if (Math.abs(vg.mag) > 0 && (val - chosenRadius * Math.abs(vg.mag)) <= chosenIntensity && chosenIntensity <= (val + chosenRadius * Math.abs(vg.mag))) {
                            voxelColor.a = chosenColor.a * (1 - (1/chosenRadius) * (Math.abs(chosenIntensity - val) / Math.abs(vg.mag)));
                        } else {
                            voxelColor.a = 0;
                        }

                        //Back to front, from the slides
                        colorOut.r = voxelColor.a*chosenColor.r + (1-voxelColor.a)*colorIn.r;
                        colorOut.g = voxelColor.a*chosenColor.g + (1-voxelColor.a)*colorIn.g;
                        colorOut.b = voxelColor.a*chosenColor.b + (1-voxelColor.a)*colorIn.b;
                        colorOut.a = (1-voxelColor.a)*colorIn.a;

                        colorIn = colorOut; 
                    }

                }
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                //Use colorOut instead of voxelColor
                int c_alpha = (1-colorOut.a) <= 1.0 ? (int) Math.floor((1-colorOut.a) * 255) : 255;
                int c_red = colorOut.r <= 1.0 ? (int) Math.floor(colorOut.r * 255) : 255;
                int c_green = colorOut.g <= 1.0 ? (int) Math.floor(colorOut.g * 255) : 255;
                int c_blue = colorOut.b <= 1.0 ? (int) Math.floor(colorOut.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
                
                //set skipped pixels to same value during interactive mode
                if(image_steps>1){
                    if(j+1<image.getHeight() && i+1<image.getWidth()){
                        image.setRGB(i, j+1, pixelColor);
                        image.setRGB(i+1, j, pixelColor);
                        image.setRGB(i+1, j+1, pixelColor);
                    }
                    if(j+2<image.getHeight() && i+2<image.getWidth()){
                        image.setRGB(i, j+2, pixelColor);
                        image.setRGB(i+1, j+2, pixelColor);
                        image.setRGB(i+2, j, pixelColor);
                        image.setRGB(i+2, j+1, pixelColor);
                        image.setRGB(i+2, j+2, pixelColor);
                    }
                }
            }
        }
    }
    
        
    void MIP(double[] viewMatrix) {
        
        //efficiency variables
        boolean use_TLIP = true;
        int ray_steps = 1;
        int image_steps=1;
        if(interactiveMode){
            use_TLIP = false;
            ray_steps = 3;
            image_steps = 2;
        }
        

        // clear image SETS IMAGE CONTENT TO 0 FOR ALL PIXELS
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        // THIS IS THE PLANE THAT DEFINES THE SLICE
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        // (IMAGECENTER,IMAGECENTER) IS THE CENTER OF THE IMAGE
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        
        int depth = (int) Math.floor(Math.sqrt( Math.pow(volume.getDimX(), 2) + Math.pow(volume.getDimY(), 2) + Math.pow(volume.getDimZ(), 2)));
        
        for (int j = 0; j < image.getHeight(); j=j+image_steps) {
            for (int i = 0; i < image.getWidth(); i=i+image_steps) {
                int maxval = 0;
                
                for(int ray = 0; ray < depth; ray=ray+ray_steps){
                    
                    
                    pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                            + volumeCenter[0] + viewVec[0] * (ray-imageCenter);
                    pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                            + volumeCenter[1] + viewVec[1] * (ray-imageCenter);
                    pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                            + volumeCenter[2] + viewVec[2] * (ray-imageCenter) ;

                    int val = getVoxel(pixelCoord, use_TLIP);

                    if(val > maxval){
                        maxval = val;
                    }
                    
                }
                
                // Map the intensity to a grey value by linear scaling
                voxelColor.r = maxval/max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = maxval > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                // Alternatively, apply the transfer function to obtain a color
                // voxelColor = tFunc.getColor(val);

                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
                
                //set skipped pixels to pixelcolor in interactive mode 
                if(image_steps>1){
                    if(j+1<image.getHeight() && i+1<image.getWidth()){
                        image.setRGB(i, j+1, pixelColor);
                        image.setRGB(i+1, j, pixelColor);
                        image.setRGB(i+1, j+1, pixelColor);
                    }
                }
            }
        }

    }    


    private void drawBoundingBox(GL2 gl) {
        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);
        gl.glLineWidth(1.5f);
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glPopAttrib();

    }

    @Override
    public void visualize(GL2 gl) {

        
        if (volume == null) {
            return;
        }

        drawBoundingBox(gl);

        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, viewMatrix, 0);

        long startTime = System.currentTimeMillis();
        slicer(viewMatrix);    
        
        switch(option) {
            //slicer
            case 0 :    slicer(viewMatrix);
                break;
            //MIP
            case 1 :    MIP(viewMatrix);
                break;
            //Compositing
            case 2 :    panel.compositingButton.setSelected(true);
                        compositing(viewMatrix, panel.jCheckBox1.isSelected());
                break;
            //2D transfer function
            case 3 :    twoD(viewMatrix, panel.jCheckBox1.isSelected());
                //break;
        }
        
        long endTime = System.currentTimeMillis();
        double runningTime = (endTime - startTime);
        panel.setSpeedLabel(Double.toString(runningTime));

        Texture texture = AWTTextureIO.newTexture(gl.getGLProfile(), image, false);

        gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        // draw rendered image as a billboard texture
        texture.enable(gl);
        texture.bind(gl);
        double halfWidth = image.getWidth() / 2.0;
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl.glTexCoord2d(0.0, 0.0);
        gl.glVertex3d(-halfWidth, -halfWidth, 0.0);
        gl.glTexCoord2d(0.0, 1.0);
        gl.glVertex3d(-halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 1.0);
        gl.glVertex3d(halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 0.0);
        gl.glVertex3d(halfWidth, -halfWidth, 0.0);
        gl.glEnd();
        texture.disable(gl);
        texture.destroy(gl);
        gl.glPopMatrix();

        gl.glPopAttrib();


        if (gl.glGetError() > 0) {
            System.out.println("some OpenGL error: " + gl.glGetError());
        }

    }
    private BufferedImage image;
    private double[] viewMatrix = new double[4 * 4];

    @Override
    public void changed() {
        for (int i=0; i < listeners.size(); i++) {
            listeners.get(i).changed();
        }
    }
    
    public void setButton(int button) {
        option = button;
    }
}
