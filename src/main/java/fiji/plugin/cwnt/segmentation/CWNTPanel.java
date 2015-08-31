package fiji.plugin.cwnt.segmentation;

import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory.ALPHA_PARAMETER;
import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory.BETA_PARAMETER;
import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory.DELTA_PARAMETER;
import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory.EPSILON_PARAMETER;
import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory.GAMMA_PARAMETER;
import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory.KAPPA_PARAMETER;
import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory.N_AD_PARAMETER;
import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory.SIGMA_F_PARAMETER;
import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory.SIGMA_G_PARAMETER;
import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory.THRESHOLD_FACTOR_PARAMETER;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_TARGET_CHANNEL;
import static fiji.plugin.trackmate.gui.TrackMateWizard.BIG_FONT;
import static fiji.plugin.trackmate.gui.TrackMateWizard.FONT;
import static fiji.plugin.trackmate.gui.TrackMateWizard.SMALL_FONT;
import ij.ImagePlus;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import fiji.plugin.cwnt.gui.DoubleJSlider;
import fiji.plugin.trackmate.gui.ConfigurationPanel;
import fiji.util.NumberParser;

public class CWNTPanel extends ConfigurationPanel
{

	private static final long serialVersionUID = 1L;

	private final int scale = 10; // sliders resolution

	private JTabbedPane tabbedPane;

	private final double[] maskingParams;

	private double thresholdFactor;

	private double[] oldMaskingParams = new double[ 9 ];

	private DoubleJSlider gaussFiltSigmaSlider;

	private JFormattedTextField gaussFiltSigmaText;

	private JFormattedTextField aniDiffNIterText;

	private DoubleJSlider aniDiffNIterSlider;

	private JFormattedTextField aniDiffKappaText;

	private DoubleJSlider aniDiffKappaSlider;

	private JFormattedTextField gaussGradSigmaText;

	private DoubleJSlider gaussGradSigmaSlider;

	private DoubleJSlider gammaSlider;

	private JFormattedTextField gammaText;

	private DoubleJSlider alphaSlider;

	private JFormattedTextField alphaText;

	private DoubleJSlider betaSlider;

	private JFormattedTextField betaText;

	private DoubleJSlider epsilonSlider;

	private JFormattedTextField epsilonText;

	private JFormattedTextField deltaText;

	private DoubleJSlider deltaSlider;

	private JFormattedTextField thresholdFactorText;

	private DoubleJSlider thresholdFactorSlider;

	private boolean liveLaunched;

	private final ImagePlus targetImp;

	private CWNTLivePreviewer previewer;

	private double oldThresholdFactor;

	JLabel labelDurationEstimate;;

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public CWNTPanel( final ImagePlus imp )
	{
		targetImp = imp;
		maskingParams = CrownWearingSegmenterFactory.collectMaskingParameters( new CrownWearingSegmenterFactory().getDefaultSettings() );
		initGUI();
		setSize( 293, 438 );
	}

	@Override
	public void setSettings( final Map< String, Object > settings )
	{
		gaussFiltSigmaText.setText( "" + settings.get( SIGMA_F_PARAMETER ) );
		aniDiffNIterText.setText( "" + settings.get( N_AD_PARAMETER ) );
		aniDiffKappaText.setText( "" + settings.get( KAPPA_PARAMETER ) );
		gaussGradSigmaText.setText( "" + settings.get( SIGMA_G_PARAMETER ) );
		gammaText.setText( "" + settings.get( GAMMA_PARAMETER ) );
		alphaText.setText( "" + settings.get( ALPHA_PARAMETER ) );
		betaText.setText( "" + settings.get( BETA_PARAMETER ) );
		epsilonText.setText( "" + settings.get( EPSILON_PARAMETER ) );
		deltaText.setText( "" + settings.get( DELTA_PARAMETER ) );
		thresholdFactorText.setText( "" + settings.get( THRESHOLD_FACTOR_PARAMETER ) );
		collectMaskingParameters();
	}

	@Override
	public Map< String, Object > getSettings()
	{
		collectMaskingParameters();
		final HashMap< String, Object > settings = new HashMap< String, Object >( 10 );
		settings.put( SIGMA_F_PARAMETER, maskingParams[ 0 ] );
		settings.put( N_AD_PARAMETER, ( int ) maskingParams[ 1 ] ); // nAD
		settings.put( KAPPA_PARAMETER, maskingParams[ 2 ] );
		settings.put( SIGMA_G_PARAMETER, maskingParams[ 3 ] );
		settings.put( GAMMA_PARAMETER, maskingParams[ 4 ] );
		settings.put( ALPHA_PARAMETER, maskingParams[ 5 ] );
		settings.put( BETA_PARAMETER, maskingParams[ 6 ] );
		settings.put( EPSILON_PARAMETER, maskingParams[ 7 ] );
		settings.put( DELTA_PARAMETER, maskingParams[ 8 ] );
		settings.put( THRESHOLD_FACTOR_PARAMETER, NumberParser.parseDouble( thresholdFactorText.getText() ) );
		settings.put( KEY_TARGET_CHANNEL, 0 );
		return settings;
	}

	public ImagePlus getTargetImagePlus()
	{
		return targetImp;
	}

	/*
	 * PRIVATE METHODS
	 */

	private void fireEvent( final ActionEvent event )
	{
		if ( event == STEP1_PARAMETER_CHANGED ||
				event == STEP2_PARAMETER_CHANGED ||
				event == STEP3_PARAMETER_CHANGED ||
				event == STEP4_PARAMETER_CHANGED ||
				event == STEP5_PARAMETER_CHANGED )
		{
			collectMaskingParameters();
			try
			{
				thresholdFactor = Double.parseDouble( thresholdFactorText.getText() );
			}
			catch ( final NumberFormatException nfe )
			{}

			if ( Arrays.equals( maskingParams, oldMaskingParams ) && thresholdFactor == oldThresholdFactor )
			{
				// We do not fire event if params did not change 
				return;
			}

			oldThresholdFactor = thresholdFactor;
			oldMaskingParams = Arrays.copyOf( maskingParams, maskingParams.length );
		}

		for ( final ActionListener listener : actionListeners )
		{
			listener.actionPerformed( event );
		}
	}

	private void collectMaskingParameters() throws NumberFormatException
	{
		try
		{
			maskingParams[ 0 ] = Double.parseDouble( gaussFiltSigmaText.getText() );
		}
		catch ( final NumberFormatException nfe )
		{}
		try
		{
			maskingParams[ 1 ] = Integer.parseInt( aniDiffNIterText.getText() );
		}
		catch ( final NumberFormatException nfe )
		{}
		try
		{
			maskingParams[ 2 ] = Double.parseDouble( aniDiffKappaText.getText() );
		}
		catch ( final NumberFormatException nfe )
		{}
		try
		{
			maskingParams[ 3 ] = Double.parseDouble( gaussGradSigmaText.getText() );
		}
		catch ( final NumberFormatException nfe )
		{}
		try
		{
			maskingParams[ 4 ] = Double.parseDouble( gammaText.getText() );
		}
		catch ( final NumberFormatException nfe )
		{}
		try
		{
			maskingParams[ 5 ] = Double.parseDouble( alphaText.getText() );
		}
		catch ( final NumberFormatException nfe )
		{}
		try
		{
			maskingParams[ 6 ] = Double.parseDouble( betaText.getText() );
		}
		catch ( final NumberFormatException nfe )
		{}
		try
		{
			maskingParams[ 7 ] = Double.parseDouble( epsilonText.getText() );
		}
		catch ( final NumberFormatException nfe )
		{}
		try
		{
			maskingParams[ 8 ] = Double.parseDouble( deltaText.getText() );
		}
		catch ( final NumberFormatException nfe )
		{}
	}

	private void link( final DoubleJSlider slider, final JTextField text )
	{
		slider.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				text.setText( "" + slider.getScaledValue() );
			}
		} );
		text.addKeyListener( new KeyAdapter()
		{
			@Override
			public void keyReleased( final KeyEvent ke )
			{
				final String typed = text.getText();
				try
				{
					final double value = Double.parseDouble( typed ) * slider.scale;
					slider.setValue( ( int ) value );
				}
				catch ( final NumberFormatException nfe )
				{}
			}
		} );
	}

	private void launchSingleFrameSegmentation()
	{
		final CWNTFrameSegmenter segmenter = new CWNTFrameSegmenter( this );
		segmenter.process();
	}

	private void launchLive()
	{
		new Thread()
		{
			@Override
			public void run()
			{
				previewer = new CWNTLivePreviewer( CWNTPanel.this );
			}
		}.start();
	}

	private void stopLive()
	{
		previewer.quit();
	}

	private void initGUI()
	{
		setLayout( new BorderLayout() );

		tabbedPane = new JTabbedPane( JTabbedPane.TOP );
		add( tabbedPane );

		{
			final JPanel panelIntroduction = new JPanel();
			tabbedPane.addTab( "Intro", null, panelIntroduction, null );
			panelIntroduction.setLayout( null );

			final JLabel lblCrownwearingNucleiTracker = new JLabel( "Crown-Wearing Nuclei Segmenter" );
			lblCrownwearingNucleiTracker.setFont( BIG_FONT );
			lblCrownwearingNucleiTracker.setHorizontalAlignment( SwingConstants.CENTER );
			lblCrownwearingNucleiTracker.setBounds( 10, 11, 268, 30 );
			panelIntroduction.add( lblCrownwearingNucleiTracker );

			final JLabel labelIntro = new JLabel( INTRO_TEXT );
			labelIntro.setFont( SMALL_FONT.deriveFont( 11f ) );
			labelIntro.setBounds( 10, 52, 268, 173 );
			panelIntroduction.add( labelIntro );

			final JButton btnTestParamtersLive = new JButton( "<html><div align=\"center\">Live test parameters</dic></html>" );
			btnTestParamtersLive.setBounds( 10, 292, 103, 72 );
			btnTestParamtersLive.setFont( FONT );
			liveLaunched = false;
			btnTestParamtersLive.addActionListener( new ActionListener()
			{
				@Override
				public void actionPerformed( final ActionEvent e )
				{
					if ( liveLaunched )
					{
						stopLive();
						btnTestParamtersLive.setText( "<html><div align=\"center\">Live test parameters</div></html>" );
						liveLaunched = false;
					}
					else
					{
						launchLive();
						btnTestParamtersLive.setText( "<html><div align=\"center\">Stop live test</div></html>" );
						liveLaunched = true;
					}
				}
			} );
			panelIntroduction.add( btnTestParamtersLive );

			final String segFrameButtonText = "<html><CENTER>Segment current frame</center></html>";
			final JButton segFrameButton = new JButton( segFrameButtonText );
			segFrameButton.addActionListener( new ActionListener()
			{
				@Override
				public void actionPerformed( final ActionEvent e )
				{
					new Thread( "CWNT thread" )
					{
						@Override
						public void run()
						{
							segFrameButton.setText( "Segmenting..." );
							segFrameButton.setEnabled( false );
							try
							{
								launchSingleFrameSegmentation();
							}
							finally
							{
								segFrameButton.setEnabled( true );
								segFrameButton.setText( segFrameButtonText );
							}
						}
					}.start();
				}
			} );
			segFrameButton.setFont( FONT );
			segFrameButton.setBounds( 175, 292, 103, 72 );
			panelIntroduction.add( segFrameButton );

			labelDurationEstimate = new JLabel();
			labelDurationEstimate.setBounds( 10, 375, 268, 14 );
			panelIntroduction.setFont( FONT );
			panelIntroduction.add( labelDurationEstimate );
		}

		final JPanel panelParams1 = new JPanel();
		{
			tabbedPane.addTab( "Param set 1", null, panelParams1, null );
			panelParams1.setLayout( null );

			final JLabel lblFiltering = new JLabel( "1. Filtering" );
			lblFiltering.setFont( BIG_FONT );
			lblFiltering.setBounds( 10, 11, 268, 29 );
			panelParams1.add( lblFiltering );

			{
				final JLabel lblGaussianFilter = new JLabel( "Gaussian filter \u03C3:" );
				lblGaussianFilter.setFont( SMALL_FONT );
				lblGaussianFilter.setBounds( 10, 51, 268, 14 );
				panelParams1.add( lblGaussianFilter );

				gaussFiltSigmaSlider = new DoubleJSlider( 0, 5 * scale, ( int ) ( maskingParams[ 0 ] * scale ), scale );
				gaussFiltSigmaSlider.setBounds( 10, 76, 223, 23 );
				panelParams1.add( gaussFiltSigmaSlider );

				gaussFiltSigmaText = new JFormattedTextField( new Double( maskingParams[ 0 ] ) );
				gaussFiltSigmaText.setHorizontalAlignment( SwingConstants.CENTER );
				gaussFiltSigmaText.setBounds( 243, 76, 35, 23 );
				gaussFiltSigmaText.setFont( FONT );
				panelParams1.add( gaussFiltSigmaText );

				link( gaussFiltSigmaSlider, gaussFiltSigmaText );
				gaussFiltSigmaSlider.addChangeListener( step1ChangeListener );
				gaussFiltSigmaText.addKeyListener( step1KeyListener );
			}

			final JLabel lblAnisotropicDiffusion = new JLabel( "2. Anisotropic diffusion" );
			lblAnisotropicDiffusion.setFont( BIG_FONT );
			lblAnisotropicDiffusion.setBounds( 10, 128, 268, 29 );
			panelParams1.add( lblAnisotropicDiffusion );

			{
				final JLabel lblNumberOfIterations = new JLabel( "Number of iterations:" );
				lblNumberOfIterations.setFont( SMALL_FONT );
				lblNumberOfIterations.setBounds( 10, 168, 268, 14 );
				panelParams1.add( lblNumberOfIterations );

				aniDiffNIterText = new JFormattedTextField( new Integer( ( int ) maskingParams[ 1 ] ) );
				aniDiffNIterText.setHorizontalAlignment( SwingConstants.CENTER );
				aniDiffNIterText.setFont( FONT );
				aniDiffNIterText.setBounds( 243, 193, 35, 23 );
				panelParams1.add( aniDiffNIterText );

				aniDiffNIterSlider = new DoubleJSlider( 0, 10, ( int ) maskingParams[ 1 ], 1 );
				aniDiffNIterSlider.setBounds( 10, 193, 223, 23 );
				panelParams1.add( aniDiffNIterSlider );

				link( aniDiffNIterSlider, aniDiffNIterText );
				aniDiffNIterSlider.addChangeListener( step2ChangeListener );
				aniDiffNIterText.addKeyListener( step2KeyListener );

			}

			{
				final JLabel lblGradientDiffusionThreshold = new JLabel( "Gradient diffusion threshold \u03BA:" );
				lblGradientDiffusionThreshold.setFont( SMALL_FONT );
				lblGradientDiffusionThreshold.setBounds( 10, 227, 268, 14 );
				panelParams1.add( lblGradientDiffusionThreshold );

				aniDiffKappaText = new JFormattedTextField( new Double( maskingParams[ 2 ] ) );
				aniDiffKappaText.setHorizontalAlignment( SwingConstants.CENTER );
				aniDiffKappaText.setFont( FONT );
				aniDiffKappaText.setBounds( 243, 252, 35, 23 );
				panelParams1.add( aniDiffKappaText );

				aniDiffKappaSlider = new DoubleJSlider( 1, 100, ( int ) maskingParams[ 2 ], 1 );
				aniDiffKappaSlider.setBounds( 10, 252, 223, 23 );
				panelParams1.add( aniDiffKappaSlider );

				link( aniDiffKappaSlider, aniDiffKappaText );
				aniDiffKappaSlider.addChangeListener( step2ChangeListener );
				aniDiffKappaText.addKeyListener( step2KeyListener );
			}

			final JLabel lblDerivativesCalculation = new JLabel( "3. Derivatives calculation" );
			lblDerivativesCalculation.setFont( BIG_FONT );
			lblDerivativesCalculation.setBounds( 10, 298, 268, 29 );
			panelParams1.add( lblDerivativesCalculation );

			{
				final JLabel lblGaussianGradient = new JLabel( "Gaussian gradient \u03C3:" );
				lblGaussianGradient.setFont( FONT );
				lblGaussianGradient.setBounds( 10, 338, 268, 14 );
				panelParams1.add( lblGaussianGradient );

				gaussGradSigmaText = new JFormattedTextField( new Double( maskingParams[ 3 ] ) );
				gaussGradSigmaText.setFont( FONT );
				gaussGradSigmaText.setHorizontalAlignment( SwingConstants.CENTER );
				gaussGradSigmaText.setBounds( 243, 363, 35, 23 );
				panelParams1.add( gaussGradSigmaText );

				gaussGradSigmaSlider = new DoubleJSlider( 0, 5 * scale, ( int ) ( maskingParams[ 3 ] * scale ), scale );
				gaussGradSigmaSlider.setBounds( 10, 363, 223, 23 );
				panelParams1.add( gaussGradSigmaSlider );

				link( gaussGradSigmaSlider, gaussGradSigmaText );
				gaussGradSigmaSlider.addChangeListener( step3ChangeListener );
				gaussGradSigmaText.addKeyListener( step3KeyListener );
			}

		}

		final JPanel panelParams2 = new JPanel();
		{
			tabbedPane.addTab( "Param set 2", panelParams2 );
			panelParams2.setLayout( null );

			final JLabel lblMasking = new JLabel( "4. Masking" );
			lblMasking.setFont( BIG_FONT );
			lblMasking.setBounds( 10, 11, 268, 29 );
			panelParams2.add( lblMasking );

			{
				final JLabel gammeLabel = new JLabel( "\u03B3: tanh shift" );
				gammeLabel.setFont( SMALL_FONT );
				gammeLabel.setBounds( 10, 51, 268, 14 );
				panelParams2.add( gammeLabel );

				gammaSlider = new DoubleJSlider( -5 * scale, 5 * scale, ( int ) ( maskingParams[ 4 ] * scale ), scale );
				gammaSlider.setBounds( 10, 76, 223, 23 );
				panelParams2.add( gammaSlider );

				gammaText = new JFormattedTextField( new Double( maskingParams[ 4 ] ) );
				gammaText.setFont( FONT );
				gammaText.setBounds( 243, 76, 35, 23 );
				panelParams2.add( gammaText );

				link( gammaSlider, gammaText );
				gammaSlider.addChangeListener( step4ChangeListener );
				gammaSlider.addKeyListener( step4KeyListener );
			}
			{
				final JLabel lblNewLabel_3 = new JLabel( "\u03B1: gradient prefactor" );
				lblNewLabel_3.setFont( SMALL_FONT );
				lblNewLabel_3.setBounds( 10, 110, 268, 14 );
				panelParams2.add( lblNewLabel_3 );

				alphaSlider = new DoubleJSlider( 0, 20 * scale, ( int ) ( maskingParams[ 5 ] * scale ), scale );
				alphaSlider.setBounds( 10, 135, 223, 23 );
				panelParams2.add( alphaSlider );

				alphaText = new JFormattedTextField( new Double( maskingParams[ 5 ] ) );
				alphaText.setFont( FONT );
				alphaText.setBounds( 243, 135, 35, 23 );
				panelParams2.add( alphaText );

				link( alphaSlider, alphaText );
				alphaSlider.addChangeListener( step4ChangeListener );
				alphaText.addKeyListener( step4KeyListener );
			}
			{
				final JLabel betaLabel = new JLabel( "\u03B2: positive laplacian magnitude prefactor" );
				betaLabel.setFont( SMALL_FONT );
				betaLabel.setBounds( 10, 169, 268, 14 );
				panelParams2.add( betaLabel );

				betaSlider = new DoubleJSlider( 0, 20 * scale, ( int ) ( maskingParams[ 6 ] * scale ), scale );
				betaSlider.setBounds( 10, 194, 223, 23 );
				panelParams2.add( betaSlider );

				betaText = new JFormattedTextField( new Double( maskingParams[ 6 ] ) );
				betaText.setFont( FONT );
				betaText.setBounds( 243, 194, 35, 23 );
				panelParams2.add( betaText );

				link( betaSlider, betaText );
				betaSlider.addChangeListener( step4ChangeListener );
				betaText.addKeyListener( step4KeyListener );
			}
			{
				final JLabel epsilonLabel = new JLabel( "\u03B5: negative hessian magnitude" );
				epsilonLabel.setFont( SMALL_FONT );
				epsilonLabel.setBounds( 10, 228, 268, 14 );
				panelParams2.add( epsilonLabel );

				epsilonSlider = new DoubleJSlider( 0, 20 * scale, ( int ) ( scale * maskingParams[ 7 ] ), scale );
				epsilonSlider.setBounds( 10, 253, 223, 23 );
				panelParams2.add( epsilonSlider );

				epsilonText = new JFormattedTextField( new Double( maskingParams[ 7 ] ) );
				epsilonText.setFont( FONT );
				epsilonText.setText( "" + maskingParams[ 7 ] );
				epsilonText.setBounds( 243, 253, 35, 23 );
				panelParams2.add( epsilonText );

				link( epsilonSlider, epsilonText );
				epsilonSlider.addChangeListener( step4ChangeListener );
				epsilonText.addKeyListener( step4KeyListener );
			}
			{
				final JLabel deltaLabel = new JLabel( "\u03B4: derivatives sum scale" );
				deltaLabel.setFont( SMALL_FONT );
				deltaLabel.setBounds( 10, 287, 268, 14 );
				panelParams2.add( deltaLabel );

				deltaText = new JFormattedTextField( new Double( maskingParams[ 8 ] ) );
				deltaText.setFont( FONT );
				deltaText.setText( "" + maskingParams[ 8 ] );
				deltaText.setBounds( 243, 312, 35, 23 );
				panelParams2.add( deltaText );

				deltaSlider = new DoubleJSlider( 0, 5 * scale, ( int ) ( maskingParams[ 8 ] * scale ), scale );
				deltaSlider.setBounds( 10, 312, 223, 23 );
				panelParams2.add( deltaSlider );

				link( deltaSlider, deltaText );
				deltaSlider.addChangeListener( step4ChangeListener );
				deltaText.addKeyListener( step4KeyListener );
			}

			final JLabel lblEquation = new JLabel( "<html>M = \u00BD ( 1 + <i>tanh</i> ( \u03B3 - ( \u03B1 G + \u03B2 L + \u03B5 H ) / \u03B4 ) )</html>" );
			lblEquation.setHorizontalAlignment( SwingConstants.CENTER );
			lblEquation.setFont( FONT.deriveFont( 12f ) );
			lblEquation.setBounds( 10, 364, 268, 35 );
			panelParams2.add( lblEquation );
		}

		{
			final JPanel panel = new JPanel();
			tabbedPane.addTab( "Param set 3", null, panel, null );
			panel.setLayout( null );

			final JLabel labelThresholding = new JLabel( "5. Thresholding" );
			labelThresholding.setBounds( 10, 11, 268, 28 );
			labelThresholding.setFont( BIG_FONT );
			panel.add( labelThresholding );

			final JLabel labelThresholdFactor = new JLabel( "Threshold factor:" );
			labelThresholdFactor.setFont( SMALL_FONT );
			labelThresholdFactor.setBounds( 10, 50, 268, 14 );
			panel.add( labelThresholdFactor );

			thresholdFactorSlider = new DoubleJSlider( 0, 5 * scale, ( int ) ( thresholdFactor * scale ), scale );
			thresholdFactorSlider.setBounds( 10, 75, 223, 23 );
			panel.add( thresholdFactorSlider );

			thresholdFactorText = new JFormattedTextField( new Double( thresholdFactor ) );
			thresholdFactorText.setFont( FONT );
			thresholdFactorText.setBounds( 243, 75, 35, 23 );
			panel.add( thresholdFactorText );

			link( thresholdFactorSlider, thresholdFactorText );
			thresholdFactorSlider.addChangeListener( step5ChangeListener );
			thresholdFactorText.addKeyListener( step5KeyListener );

		}
	}

	/*
	 * EVENTS
	 */

	/**
	 * This event is fired whenever the value of σ for the gaussian filtering in
	 * step 1 is changed by this GUI.
	 */
	final ActionEvent STEP1_PARAMETER_CHANGED = new ActionEvent( this, 0, "GaussianFilteringParameterChanged" );

	/**
	 * This event is fired whenever parameters defining the anisotropic
	 * diffusion step 2 is changed by this GUI.
	 */
	final ActionEvent STEP2_PARAMETER_CHANGED = new ActionEvent( this, 1, "AnisotropicDiffusionParameterChanged" );

	/**
	 * This event is fired whenever the value of σ for the gaussian derivatives
	 * computation in step 3 is changed by this GUI.
	 */
	final ActionEvent STEP3_PARAMETER_CHANGED = new ActionEvent( this, 2, "DerivativesParameterChanged" );

	/**
	 * This event is fired whenever parameters specifying how to combine the
	 * final mask in step 4 is changed by this GUI.
	 */
	final ActionEvent STEP4_PARAMETER_CHANGED = new ActionEvent( this, 3, "MaskingParameterChanged" );

	/**
	 * This event is fired whenever the thresholding parameters are changed by
	 * this GUI.
	 */
	final ActionEvent STEP5_PARAMETER_CHANGED = new ActionEvent( this, 4, "ThresholdingParameterChanged" );

//	/** This event is fired when the user presses the 'go' button in the 4th tab. */
//	public final ActionEvent GO_BUTTON_PRESSED = new ActionEvent(this, 4, "GoButtonPressed");
//	/** This event is fired when the user changes the current tab. */
//	public final ActionEvent TAB_CHANGED = new ActionEvent(this, 5, "TabChanged");

	/*
	 * LOCAL LISTENERS
	 */

	private final ChangeListener step1ChangeListener = new ChangeListener()
	{
		@Override
		public void stateChanged( final ChangeEvent e )
		{
			fireEvent( STEP1_PARAMETER_CHANGED );
		}
	};

	private final ChangeListener step2ChangeListener = new ChangeListener()
	{
		@Override
		public void stateChanged( final ChangeEvent e )
		{
			fireEvent( STEP2_PARAMETER_CHANGED );
		}
	};

	private final ChangeListener step3ChangeListener = new ChangeListener()
	{
		@Override
		public void stateChanged( final ChangeEvent e )
		{
			fireEvent( STEP3_PARAMETER_CHANGED );
		}
	};

	private final ChangeListener step4ChangeListener = new ChangeListener()
	{
		@Override
		public void stateChanged( final ChangeEvent e )
		{
			fireEvent( STEP4_PARAMETER_CHANGED );
		}
	};

	private final ChangeListener step5ChangeListener = new ChangeListener()
	{
		@Override
		public void stateChanged( final ChangeEvent e )
		{
			fireEvent( STEP5_PARAMETER_CHANGED );
		}
	};

	private final KeyListener step1KeyListener = new KeyAdapter()
	{
		@Override
		public void keyReleased( final KeyEvent ke )
		{
			fireEvent( STEP1_PARAMETER_CHANGED );
		}
	};

	private final KeyListener step2KeyListener = new KeyAdapter()
	{
		@Override
		public void keyReleased( final KeyEvent ke )
		{
			fireEvent( STEP2_PARAMETER_CHANGED );
		}
	};

	private final KeyListener step3KeyListener = new KeyAdapter()
	{
		@Override
		public void keyReleased( final KeyEvent ke )
		{
			fireEvent( STEP3_PARAMETER_CHANGED );
		}
	};

	private final KeyListener step4KeyListener = new KeyAdapter()
	{
		@Override
		public void keyReleased( final KeyEvent ke )
		{
			fireEvent( STEP4_PARAMETER_CHANGED );
		}
	};

	private final KeyListener step5KeyListener = new KeyAdapter()
	{
		@Override
		public void keyReleased( final KeyEvent ke )
		{
			fireEvent( STEP5_PARAMETER_CHANGED );
		}
	};

	public static final String INTRO_TEXT = "<html>" +
			"<div align=\"justify\">" +
			"This plugin allows the segmentation and tracking of bright blobs objects, " +
			"typically nuclei imaged in 3D over time. " +
			"<p>" +
			"Because the crown-like mask needs 10 parameters to be specified, this panel offers " +
			"to test the value of each parameter. If you press the 'live' button below, the resulting masked " +
			"image and intermediate images will be computed over a limited area of the source image, " +
			"specified by the ROI. " +
			"<p> " +
			"The 'Segment current frame button' launches the full computation over the " +
			"current 3D frame, and displays the resulting segmentation with colored " +
			"nuclei labels." +
			"</html>" +
			"";

}
