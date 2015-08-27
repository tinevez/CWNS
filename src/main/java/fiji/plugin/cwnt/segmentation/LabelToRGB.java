package fiji.plugin.cwnt.segmentation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.algorithm.OutputAlgorithm;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.labeling.Labeling;
import net.imglib2.labeling.LabelingType;
import net.imglib2.multithreading.Chunk;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Util;

@SuppressWarnings( "deprecation" )
public class LabelToRGB extends MultiThreadedBenchmarkAlgorithm implements OutputAlgorithm< Img< ARGBType >>
{

	private final Labeling< Integer > labels;

	private Img< ARGBType > rgb;

	public LabelToRGB( final Labeling< Integer > labels )
	{
		super();
		this.labels = labels;
	}

	@Override
	public boolean checkInput()
	{
		return true;
	}

	@Override
	public boolean process()
	{
		final long start = System.currentTimeMillis();

		final ImgFactory< ARGBType > factory = Util.getArrayOrCellImgFactory( labels, new ARGBType() );
		rgb = factory.create( labels, new ARGBType() );

		final Vector< Chunk > chunks = SimpleMultiThreading.divideIntoChunks( rgb.size(), numThreads );
		final Thread[] threads = new Thread[ numThreads ];

		final int nColors = GLASBEY_LUT.size();

		for ( int i = 0; i < threads.length; i++ )
		{

			final Chunk chunk = chunks.get( i );
			threads[ i ] = new Thread( "LabelToRGB thread " + i )
			{

				@Override
				public void run()
				{

					final Cursor< LabelingType< Integer >> cursor = labels.localizingCursor();
					final RandomAccess< ARGBType > target = rgb.randomAccess( labels );
					cursor.jumpFwd( chunk.getStartPosition() );

					for ( long j = 0; j < chunk.getLoopSize(); j++ )
					{
						cursor.fwd();
						target.setPosition( cursor );

						final List< Integer > labeling = cursor.get().getLabeling();
						if ( labeling.size() > 0 )
						{
							final int label = labeling.get( 0 );
							final int colorIndex = label % nColors;
							final int[] arr = GLASBEY_LUT.get( colorIndex );
							final int color = ARGBType.rgba( arr[ 0 ], arr[ 1 ], arr[ 2 ], 0 );
							target.get().set( color );
						}
					}
				}
			};
		}

		SimpleMultiThreading.startAndJoin( threads );

		final long end = System.currentTimeMillis();
		processingTime = end - start;
		return true;
	}

	@Override
	public Img< ARGBType > getResult()
	{
		return rgb;
	}

	/**
	 * The 32 first (non-white) colors of the Glabey LUT, made to maximise the
	 * differences in perceived colors for labeled image.
	 * <p>
	 * 
	 * <pre>
	 * <b>C.A. Glasbey, G.W.A.M. van der Heijden, V. Toh and A.J. Gray</b> <i>
	 * Colour displays for categorical images</i>  - Color Research and Application, 32, 304-309
	 * </pre>
	 * 
	 * {@link http://www.bioss.ac.uk/staff/chris/colorpaper.pdf}
	 */
	public static Map< Integer, int[] > GLASBEY_LUT = new HashMap< Integer, int[] >( 32 );
	static
	{
		GLASBEY_LUT.put( 0, new int[] { 0, 0, 255 } );
		GLASBEY_LUT.put( 1, new int[] { 255, 0, 0 } );
		GLASBEY_LUT.put( 2, new int[] { 0, 255, 0 } );
		GLASBEY_LUT.put( 3, new int[] { 0, 0, 51 } );
		GLASBEY_LUT.put( 4, new int[] { 255, 0, 182 } );
		GLASBEY_LUT.put( 5, new int[] { 0, 83, 0 } );
		GLASBEY_LUT.put( 6, new int[] { 255, 211, 0 } );
		GLASBEY_LUT.put( 7, new int[] { 0, 159, 255 } );
		GLASBEY_LUT.put( 8, new int[] { 154, 77, 66 } );
		GLASBEY_LUT.put( 9, new int[] { 0, 255, 190 } );
		GLASBEY_LUT.put( 10, new int[] { 120, 63, 193 } );
		GLASBEY_LUT.put( 11, new int[] { 31, 150, 152 } );
		GLASBEY_LUT.put( 12, new int[] { 255, 172, 253 } );
		GLASBEY_LUT.put( 13, new int[] { 177, 204, 113 } );
		GLASBEY_LUT.put( 14, new int[] { 241, 8, 92 } );
		GLASBEY_LUT.put( 15, new int[] { 254, 143, 66 } );
		GLASBEY_LUT.put( 16, new int[] { 221, 0, 255 } );
		GLASBEY_LUT.put( 17, new int[] { 32, 26, 1 } );
		GLASBEY_LUT.put( 18, new int[] { 114, 0, 85 } );
		GLASBEY_LUT.put( 19, new int[] { 118, 108, 149 } );
		GLASBEY_LUT.put( 20, new int[] { 2, 173, 36 } );
		GLASBEY_LUT.put( 21, new int[] { 200, 255, 0 } );
		GLASBEY_LUT.put( 22, new int[] { 136, 108, 0 } );
		GLASBEY_LUT.put( 23, new int[] { 255, 183, 159 } );
		GLASBEY_LUT.put( 24, new int[] { 133, 133, 103 } );
		GLASBEY_LUT.put( 25, new int[] { 161, 3, 0 } );
		GLASBEY_LUT.put( 26, new int[] { 20, 249, 255 } );
		GLASBEY_LUT.put( 27, new int[] { 0, 71, 158 } );
		GLASBEY_LUT.put( 28, new int[] { 220, 94, 147 } );
		GLASBEY_LUT.put( 29, new int[] { 147, 212, 255 } );
		GLASBEY_LUT.put( 30, new int[] { 0, 76, 255 } );
		GLASBEY_LUT.put( 31, new int[] { 0, 66, 80 } );
	}

}
