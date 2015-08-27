package fiji.plugin.cwnt;

import fiji.plugin.trackmate.detection.DetectionUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

import java.io.File;
import java.util.List;

import mpicbg.imglib.algorithm.gauss.GaussianGradient2D;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class GaussianGradient_TestDrive {

	@SuppressWarnings({ "unchecked", "rawtypes", "unused" })
	public static void main(final String[] args) {
		
//		File testImage = new File("/Users/tinevez/Projects/BRajaseka/Data/point.tif");
		final File testImage = new File( "/Users/tinevez/Projects/BRajasekaran/Data/Meta-nov7mdb18ssplus-embryo2-1.tif" );
		
		
		ImageJ.main(args);
		final ImagePlus imp = IJ.openImage(testImage.getAbsolutePath());
		imp.show();

		final Img< ? extends RealType > source = ImageJFunctions.wrap( imp );
		final Img floatImage = DetectionUtils.copyToFloatImg( source, source, new ArrayImgFactory< FloatType >() );
		
		System.out.print("Gaussian gradient 2D ... ");
		final GaussianGradient2D grad = new GaussianGradient2D(source, 1);
		grad.setNumThreads();
		grad.checkInput();
		grad.process();
		
		final Img norm = grad.getResult();
		final List< Img > list = grad.getGradientComponents();
		System.out.println("dt = "+grad.getProcessingTime()/1e3+" s.");
		
		
		ImageJFunctions.show( norm, "Norm" );
		ImageJFunctions.show( list.get( 0 ), "Dx" );
		ImageJFunctions.show( list.get( 1 ), "Dy" );
	}
}
