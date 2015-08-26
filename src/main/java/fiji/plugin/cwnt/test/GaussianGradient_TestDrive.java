package fiji.plugin.cwnt.test;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

import java.io.File;
import java.util.List;

import mpicbg.imglib.algorithm.gauss.GaussianGradient2D;
import mpicbg.imglib.algorithm.math.ImageConverter;
import mpicbg.imglib.function.RealTypeConverter;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.image.display.imagej.ImageJFunctions;
import mpicbg.imglib.type.numeric.RealType;
import mpicbg.imglib.type.numeric.real.FloatType;

public class GaussianGradient_TestDrive {

	@SuppressWarnings({ "unchecked", "rawtypes", "unused" })
	public static void main(String[] args) {
		
//		File testImage = new File("/Users/tinevez/Projects/BRajaseka/Data/point.tif");
		File testImage = new File("/Users/tinevez/Projects/BRajaseka/Data/Meta-nov7mdb18ssplus-embryo2-1.tif");
		
		
		ImageJ.main(args);
		ImagePlus imp = IJ.openImage(testImage.getAbsolutePath());
		imp.show();

		Image<? extends RealType> source = ImageJFunctions.wrap(imp);

		ImageConverter converter = new ImageConverter(
				source,
				new ImageFactory(new FloatType(), source.getContainerFactory()),
				new RealTypeConverter());
		converter.setNumThreads();
		converter.checkInput();
		converter.process();
		Image floatImage = converter.getResult();
		
		System.out.print("Gaussian gradient 2D ... ");
		GaussianGradient2D grad = new GaussianGradient2D(source, 1);
//		GaussianGradient2D grad = new GaussianGradient2D(floatImage, 1);
		grad.setNumThreads();
		grad.checkInput();
		grad.process();
		
		Image norm = grad.getResult();
		List<Image> list = grad.getGradientComponents();
		System.out.println("dt = "+grad.getProcessingTime()/1e3+" s.");
		
		
		ImageJFunctions.copyToImagePlus(norm).show();
		ImageJFunctions.copyToImagePlus(list.get(0)).show();
		ImageJFunctions.copyToImagePlus(list.get(1)).show();
		

		
	}
	
}
