package fiji.plugin.cwnt.gui;

import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory.KEY_SPLIT_NUCLEI;
import static fiji.plugin.trackmate.gui.TrackMateWizard.FONT;
import ij.ImagePlus;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory;
import fiji.plugin.cwnt.segmentation.NucleiMasker;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.gui.ConfigurationPanel;

public class CwntGui extends ConfigurationPanel
{

	private static final long serialVersionUID = 1L;

	/*
	 * EVENTS
	 */
	/**
	 * This event is fired whenever the value of σ for the gaussian filtering in
	 * step 1 is changed by this GUI.
	 */
	public final ActionEvent STEP1_PARAMETER_CHANGED = new ActionEvent( this, 0, "GaussianFilteringParameterChanged" );

	/**
	 * This event is fired whenever parameters defining the anisotropic
	 * diffusion step 2 is changed by this GUI.
	 */
	public final ActionEvent STEP2_PARAMETER_CHANGED = new ActionEvent( this, 1, "AnisotropicDiffusionParameterChanged" );

	/**
	 * This event is fired whenever the value of σ for the gaussian derivatives
	 * computation in step 3 is changed by this GUI.
	 */
	public final ActionEvent STEP3_PARAMETER_CHANGED = new ActionEvent( this, 2, "DerivativesParameterChanged" );

	/**
	 * This event is fired whenever parameters specifying how to combine the
	 * final mask in step 4 is changed by this GUI.
	 */
	public final ActionEvent STEP4_PARAMETER_CHANGED = new ActionEvent( this, 3, "MaskingParameterChanged" );

	/**
	 * This event is fired when the user presses the 'go' button in the 4th tab.
	 */
	public final ActionEvent GO_BUTTON_PRESSED = new ActionEvent( this, 4, "GoButtonPressed" );

	/** This event is fired when the user changes the current tab. */
	public final ActionEvent TAB_CHANGED = new ActionEvent( this, 5, "TabChanged" );

	/*
	 * LOCAL LISTENERS
	 */

	private final ArrayList< ActionListener > listeners = new ArrayList< ActionListener >();

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

	/*
	 * FONTS
	 */

	private final static Font SMALL_LABEL_FONT = new Font( "Arial", Font.PLAIN, 12 );

	private final static Font MEDIUM_LABEL_FONT = new Font( "Arial", Font.PLAIN, 16 );

	private final static Font BIG_LABEL_FONT = new Font( "Arial", Font.PLAIN, 20 );

	private final static Font TEXT_FIELD_FONT = new Font( "Arial", Font.PLAIN, 11 );

	/*
	 * PARAMETERS
	 */

	private double[] oldParams;

	/*
	 * GUI elements
	 */

	private JTabbedPane tabbedPane;

	private final int scale = 10;

	private final DecimalFormat df2d = new DecimalFormat( "0.####", DecimalFormatSymbols.getInstance( Locale.US ) );

	private JTextField gaussFiltSigmaText;

	private DoubleJSlider gaussFiltSigmaSlider;

	private JTextField aniDiffNIterText;

	private DoubleJSlider aniDiffNIterSlider;

	private JTextField aniDiffKappaText;

	private DoubleJSlider aniDiffKappaSlider;

	private JTextField gaussGradSigmaText;

	private DoubleJSlider gaussGradSigmaSlider;

	private DoubleJSlider gammaSlider;

	private JTextField gammaText;

	private DoubleJSlider alphaSlider;

	private JTextField alphaText;

	private DoubleJSlider betaSlider;

	private JTextField betaText;

	private JTextField epsilonText;

	private DoubleJSlider epsilonSlider;

	private JTextField deltaText;

	private DoubleJSlider deltaSlider;

	private JLabel lblEstimatedTime;

	private JLabel lblEstimatedTime_1;

	/** The go button that launches the whole segmentation. */
	public JButton btnGo;

	/**
	 * In the array, the parameters are ordered as follow:
	 * <ol start="0">
	 * <li>the σ for the gaussian filtering in step 1
	 * <li>the number of iteration for anisotropic filtering in step 2
	 * <li>κ, the gradient threshold for anisotropic filtering in step 2
	 * <li>the σ for the gaussian derivatives in step 3
	 * <li>γ, the <i>tanh</i> shift in step 4
	 * <li>α, the gradient prefactor in step 4
	 * <li>β, the laplacian positive magnitude prefactor in step 4
	 * <li>ε, the hessian negative magnitude prefactor in step 4
	 * <li>δ, the derivative sum scale in step 4
	 * </ol>
	 */
	private double[] params = NucleiMasker.DEFAULT_MASKING_PARAMETERS;

	/** The index of the tab that tune the second set of parameters. */
	public int indexPanelParameters2 = 2;

	/** The index of the tab that tune the first set of parameters. */
	public int indexPanelParameters1 = 1;

	private JLabel labelTargetImage;

	private JLabel lblGaussianFilter;

	private JLabel lblNumberOfIterations;

	private JLabel lblGradientDiffusionThreshold;

	private JLabel lblParameterSet;

	private JLabel lblMasking;

	private JLabel gammeLabel;

	private JLabel lblNewLabel_1;

	private JLabel betaLabel;

	private JLabel epsilonLabel;

	private JLabel deltaLabel;

	private JLabel lblEquation;

	private JCheckBox chckbxGenLabels;

	private JProgressBar progressBar;

	private JCheckBox chckbxShowColoredLabel;

	private final GuiLogger logger;

	private JCheckBox chckbxSplitLargeNuclei;

	/*
	 * CONSTRUCTOR
	 */

	public CwntGui( final ImagePlus imp )
	{
		initGUI();
		this.logger = new GuiLogger();
		labelTargetImage.setText( imp.getTitle() );
	}

	/*
	 * PUBLIC METHODS
	 */

	public Logger getLogger()
	{
		return logger;
	}

	@Override
	public void addActionListener( final ActionListener listener )
	{
		listeners.add( listener );
	}

	@Override
	public boolean removeActionListener( final ActionListener listener )
	{
		return listeners.remove( listener );
	}

	@Override
	public List< ActionListener > getActionListeners()
	{
		return listeners;
	}

	public void setDurationEstimate( final double t )
	{
		lblEstimatedTime_1.setText( String.format( "Processing duration estimate: %.0f min.", t ) );
	}

	@Override
	public void setSettings( final Map< String, Object > settings )
	{
		setParameters( settings );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	@Override
	public Map< String, Object > getSettings()
	{
		final Map< String, Object > settings = new CrownWearingSegmenterFactory().getDefaultSettings();
		CrownWearingSegmenterFactory.putMaskingParameters( params, settings );
		settings.put( KEY_SPLIT_NUCLEI, Boolean.valueOf( chckbxSplitLargeNuclei.isSelected() ) );
		return settings;
	}

	/**
	 * Return the parameters set by this GUI as a 9-elemts double array. In the
	 * array, the parameters are ordered as follow:
	 * <ol start="0">
	 * <li>the σ for the gaussian filtering in step 1
	 * <li>the number of iteration for anisotropic filtering in step 2
	 * <li>κ, the gradient threshold for anisotropic filtering in step 2
	 * <li>the σ for the gaussian derivatives in step 3
	 * <li>γ, the <i>tanh</i> shift in step 4
	 * <li>α, the gradient prefactor in step 4
	 * <li>β, the laplacian positive magnitude prefactor in step 4
	 * <li>ε, the hessian negative magnitude prefactor in step 4
	 * <li>δ, the derivative sum scale in step 4
	 * </ol>
	 */
	public double[] getParameters()
	{
		return params;
	}

	private void setParameters( final Map< String, Object > settings )
	{
		final double[] p = CrownWearingSegmenterFactory.collectMaskingParameters( settings );
		System.arraycopy( p, 0, this.params, 0, p.length );
		chckbxSplitLargeNuclei.setSelected( ( Boolean ) settings.get( KEY_SPLIT_NUCLEI ) );
	}

	public int getSelectedIndex()
	{
		return tabbedPane.getSelectedIndex();
	}

	public boolean getShowLabelFlag()
	{
		return chckbxGenLabels.isSelected();
	}

	public boolean getShowColorLabelFlag()
	{
		return chckbxShowColoredLabel.isSelected();
	}

	/*
	 * PRIVATE METHODS
	 */

	private double[] collectParameters() throws NumberFormatException
	{
		final double gaussFilterSigma = Double.parseDouble( gaussFiltSigmaText.getText() );
		final double nIterAnDiff = ( int ) Double.parseDouble( aniDiffNIterText.getText() );
		final double kappa = Double.parseDouble( aniDiffKappaText.getText() );
		final double gaussGradSigma = Double.parseDouble( gaussGradSigmaText.getText() );
		final double gamma = Double.parseDouble( gammaText.getText() );
		final double alpha = Double.parseDouble( alphaText.getText() );
		final double beta = Double.parseDouble( betaText.getText() );
		final double epsilon = Double.parseDouble( epsilonText.getText() );
		final double delta = Double.parseDouble( deltaText.getText() );
		return new double[] {
				gaussFilterSigma,
				nIterAnDiff,
				kappa,
				gaussGradSigma,
				gamma,
				alpha,
				beta,
				epsilon,
				delta
		};
	}

	private void fireEvent( final ActionEvent event )
	{
		if ( event == STEP1_PARAMETER_CHANGED ||
				event == STEP2_PARAMETER_CHANGED ||
				event == STEP3_PARAMETER_CHANGED ||
				event == STEP4_PARAMETER_CHANGED )
		{
			try
			{
				params = collectParameters();
			}
			catch ( final NumberFormatException nfe )
			{
				return;
			}
			if ( Arrays.equals( params, oldParams ) ) { return; }

			oldParams = Arrays.copyOf( params, params.length );
		}
		for ( final ActionListener listener : listeners )
		{
			listener.actionPerformed( event );
		}
	}

	private void initGUI()
	{

		setLayout( new BorderLayout() );

		tabbedPane = new JTabbedPane( JTabbedPane.TOP );
		tabbedPane.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				fireEvent( TAB_CHANGED );
			}
		} );
		add( tabbedPane );

		{
			final JPanel panelIntroduction = new JPanel();
			tabbedPane.addTab( "Intro", null, panelIntroduction, null );

			final JLabel lblCrownwearingNucleiTracker = new JLabel( "Crown-Wearing Nuclei Segmenter" );
			lblCrownwearingNucleiTracker.setFont( BIG_LABEL_FONT );
			lblCrownwearingNucleiTracker.setHorizontalAlignment( SwingConstants.CENTER );

			final JLabel lblTargetImage = new JLabel( "Target image:" );
			lblTargetImage.setFont( MEDIUM_LABEL_FONT );

			labelTargetImage = new JLabel();
			labelTargetImage.setHorizontalAlignment( SwingConstants.CENTER );
			labelTargetImage.setFont( MEDIUM_LABEL_FONT );

			final JLabel labelIntro = new JLabel( CrownWearingSegmenterFactory.INFO_TEXT );
			labelIntro.setFont( SMALL_LABEL_FONT.deriveFont( 11f ) );
			final GroupLayout gl_panelIntroduction = new GroupLayout( panelIntroduction );
			gl_panelIntroduction.setHorizontalGroup(
					gl_panelIntroduction.createParallelGroup( Alignment.TRAILING )
							.addGroup( gl_panelIntroduction.createSequentialGroup()
									.addGap( 10 )
									.addGroup( gl_panelIntroduction.createParallelGroup( Alignment.TRAILING )
											.addComponent( labelIntro, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 413, Short.MAX_VALUE )
											.addComponent( labelTargetImage, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 413, Short.MAX_VALUE )
											.addComponent( lblTargetImage, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 413, Short.MAX_VALUE )
											.addComponent( lblCrownwearingNucleiTracker, GroupLayout.DEFAULT_SIZE, 413, Short.MAX_VALUE ) )
									.addContainerGap() )
					);
			gl_panelIntroduction.setVerticalGroup(
					gl_panelIntroduction.createParallelGroup( Alignment.LEADING )
							.addGroup( gl_panelIntroduction.createSequentialGroup()
									.addGap( 11 )
									.addComponent( lblCrownwearingNucleiTracker, GroupLayout.PREFERRED_SIZE, 30, GroupLayout.PREFERRED_SIZE )
									.addGap( 11 )
									.addComponent( lblTargetImage )
									.addGap( 11 )
									.addComponent( labelTargetImage )
									.addPreferredGap( ComponentPlacement.UNRELATED )
									.addComponent( labelIntro, GroupLayout.DEFAULT_SIZE, 435, Short.MAX_VALUE )
									.addContainerGap() )
					);
			panelIntroduction.setLayout( gl_panelIntroduction );
		}

		final JPanel panelParams1 = new JPanel();
		{
			tabbedPane.addTab( "Param set 1", null, panelParams1, null );

			final JLabel lblNewLabel = new JLabel( "Parameter set 1" );
			lblNewLabel.setFont( BIG_LABEL_FONT );
			lblNewLabel.setHorizontalAlignment( SwingConstants.CENTER );

			final JLabel lblFiltering = new JLabel( "1. Filtering" );
			lblFiltering.setFont( MEDIUM_LABEL_FONT );

			{
				lblGaussianFilter = new JLabel( "Gaussian filter \u03C3:" );
				lblGaussianFilter.setFont( SMALL_LABEL_FONT );

				gaussFiltSigmaSlider = new DoubleJSlider( 0, 5 * scale, ( int ) ( params[ 0 ] * scale ), scale );

				gaussFiltSigmaText = new JTextField( "" + params[ 0 ] );
				gaussFiltSigmaText.setHorizontalAlignment( SwingConstants.CENTER );
				gaussFiltSigmaText.setFont( TEXT_FIELD_FONT );

				link( gaussFiltSigmaSlider, gaussFiltSigmaText );
				gaussFiltSigmaSlider.addChangeListener( step1ChangeListener );
				gaussFiltSigmaText.addKeyListener( step1KeyListener );
			}

			final JLabel lblAnisotropicDiffusion = new JLabel( "2. Anisotropic diffusion" );
			lblAnisotropicDiffusion.setFont( MEDIUM_LABEL_FONT );

			{
				lblNumberOfIterations = new JLabel( "Number of iterations:" );
				lblNumberOfIterations.setFont( SMALL_LABEL_FONT );

				aniDiffNIterText = new JTextField();
				aniDiffNIterText.setHorizontalAlignment( SwingConstants.CENTER );
				aniDiffNIterText.setText( "" + params[ 1 ] );
				aniDiffNIterText.setFont( TEXT_FIELD_FONT );

				aniDiffNIterSlider = new DoubleJSlider( 1, 10, ( int ) params[ 1 ], 1 );

				link( aniDiffNIterSlider, aniDiffNIterText );
				aniDiffNIterSlider.addChangeListener( step2ChangeListener );
				aniDiffNIterText.addKeyListener( step2KeyListener );

			}

			{
				lblGradientDiffusionThreshold = new JLabel( "Gradient diffusion threshold \u03BA:" );
				lblGradientDiffusionThreshold.setFont( SMALL_LABEL_FONT );

				aniDiffKappaText = new JTextField();
				aniDiffKappaText.setHorizontalAlignment( SwingConstants.CENTER );
				aniDiffKappaText.setText( "" + params[ 2 ] );
				aniDiffKappaText.setFont( TEXT_FIELD_FONT );

				aniDiffKappaSlider = new DoubleJSlider( 1, 100, ( int ) params[ 2 ], 1 );

				link( aniDiffKappaSlider, aniDiffKappaText );
				aniDiffKappaSlider.addChangeListener( step2ChangeListener );
				aniDiffKappaText.addKeyListener( step2KeyListener );
			}

			final JLabel lblDerivativesCalculation = new JLabel( "3. Derivatives calculation" );
			lblDerivativesCalculation.setFont( new Font( "Arial", Font.PLAIN, 16 ) );

			{
				final JLabel lblGaussianGradient = new JLabel( "Gaussian gradient \u03C3:" );
				lblGaussianGradient.setFont( new Font( "Arial", Font.PLAIN, 12 ) );

				gaussGradSigmaText = new JTextField();
				gaussGradSigmaText.setFont( TEXT_FIELD_FONT );
				gaussGradSigmaText.setHorizontalAlignment( SwingConstants.CENTER );
				gaussGradSigmaText.setText( "" + params[ 3 ] );

				gaussGradSigmaSlider = new DoubleJSlider( 0, 5 * scale, ( int ) ( params[ 3 ] * scale ), scale );

				link( gaussGradSigmaSlider, gaussGradSigmaText );
				final GroupLayout gl_panelParams1 = new GroupLayout( panelParams1 );
				gl_panelParams1.setHorizontalGroup(
						gl_panelParams1.createParallelGroup( Alignment.LEADING )
								.addGroup( gl_panelParams1.createSequentialGroup()
										.addGap( 10 )
										.addGroup( gl_panelParams1.createParallelGroup( Alignment.LEADING )
												.addComponent( lblGradientDiffusionThreshold, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE )
												.addGroup( Alignment.TRAILING, gl_panelParams1.createSequentialGroup()
														.addComponent( gaussFiltSigmaSlider, GroupLayout.DEFAULT_SIZE, 297, Short.MAX_VALUE )
														.addPreferredGap( ComponentPlacement.RELATED )
														.addComponent( gaussFiltSigmaText, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE ) )
												.addGroup( Alignment.TRAILING, gl_panelParams1.createSequentialGroup()
														.addComponent( aniDiffNIterSlider, GroupLayout.DEFAULT_SIZE, 297, Short.MAX_VALUE )
														.addPreferredGap( ComponentPlacement.RELATED )
														.addComponent( aniDiffNIterText, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE ) )
												.addGroup( Alignment.TRAILING, gl_panelParams1.createSequentialGroup()
														.addComponent( aniDiffKappaSlider, GroupLayout.DEFAULT_SIZE, 297, Short.MAX_VALUE )
														.addPreferredGap( ComponentPlacement.RELATED )
														.addComponent( aniDiffKappaText, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE ) )
												.addGroup( Alignment.TRAILING, gl_panelParams1.createSequentialGroup()
														.addComponent( gaussGradSigmaSlider, GroupLayout.DEFAULT_SIZE, 297, Short.MAX_VALUE )
														.addPreferredGap( ComponentPlacement.RELATED )
														.addComponent( gaussGradSigmaText, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE ) )
												.addComponent( lblGaussianFilter, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE )
												.addComponent( lblFiltering, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE )
												.addComponent( lblNewLabel, Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE )
												.addComponent( lblNumberOfIterations, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE )
												.addComponent( lblAnisotropicDiffusion, Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE )
												.addComponent( lblGaussianGradient, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE )
												.addComponent( lblDerivativesCalculation, Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE ) )
										.addContainerGap() )
						);
				gl_panelParams1.setVerticalGroup(
						gl_panelParams1.createParallelGroup( Alignment.LEADING )
								.addGroup( gl_panelParams1.createSequentialGroup()
										.addGap( 11 )
										.addComponent( lblNewLabel, GroupLayout.PREFERRED_SIZE, 29, GroupLayout.PREFERRED_SIZE )
										.addGap( 24 )
										.addComponent( lblFiltering, GroupLayout.PREFERRED_SIZE, 29, GroupLayout.PREFERRED_SIZE )
										.addGap( 11 )
										.addComponent( lblGaussianFilter )
										.addGap( 11 )
										.addGroup( gl_panelParams1.createParallelGroup( Alignment.LEADING )
												.addComponent( gaussFiltSigmaText, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE )
												.addComponent( gaussFiltSigmaSlider, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE ) )
										.addGap( 29 )
										.addComponent( lblAnisotropicDiffusion, GroupLayout.PREFERRED_SIZE, 29, GroupLayout.PREFERRED_SIZE )
										.addGap( 11 )
										.addComponent( lblNumberOfIterations )
										.addGap( 11 )
										.addGroup( gl_panelParams1.createParallelGroup( Alignment.LEADING )
												.addComponent( aniDiffNIterText, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE )
												.addComponent( aniDiffNIterSlider, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE ) )
										.addGap( 11 )
										.addComponent( lblGradientDiffusionThreshold )
										.addGap( 11 )
										.addGroup( gl_panelParams1.createParallelGroup( Alignment.LEADING )
												.addComponent( aniDiffKappaText, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE )
												.addComponent( aniDiffKappaSlider, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE ) )
										.addGap( 23 )
										.addComponent( lblDerivativesCalculation, GroupLayout.PREFERRED_SIZE, 29, GroupLayout.PREFERRED_SIZE )
										.addGap( 11 )
										.addComponent( lblGaussianGradient )
										.addGap( 11 )
										.addGroup( gl_panelParams1.createParallelGroup( Alignment.LEADING )
												.addComponent( gaussGradSigmaSlider, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE )
												.addComponent( gaussGradSigmaText, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE ) ) )
						);
				panelParams1.setLayout( gl_panelParams1 );
				gaussGradSigmaSlider.addChangeListener( step3ChangeListener );
				gaussGradSigmaText.addKeyListener( step3KeyListener );
			}

		}

		final JPanel panelParams2 = new JPanel();
		{
			tabbedPane.addTab( "Param set 2", panelParams2 );

			lblParameterSet = new JLabel( "Parameter set 2" );
			lblParameterSet.setHorizontalAlignment( SwingConstants.CENTER );
			lblParameterSet.setFont( BIG_LABEL_FONT );

			lblMasking = new JLabel( "4. Masking" );
			lblMasking.setFont( MEDIUM_LABEL_FONT );

			{
				gammeLabel = new JLabel( "\u03B3: tanh shift" );
				gammeLabel.setFont( SMALL_LABEL_FONT );

				gammaSlider = new DoubleJSlider( -5 * scale, 5 * scale, ( int ) ( params[ 4 ] * scale ), scale );

				gammaText = new JTextField();
				gammaText.setText( "" + params[ 4 ] );
				gammaText.setFont( TEXT_FIELD_FONT );

				link( gammaSlider, gammaText );
				gammaSlider.addChangeListener( step4ChangeListener );
				gammaSlider.addKeyListener( step4KeyListener );
			}
			{
				lblNewLabel_1 = new JLabel( "\u03B1: gradient prefactor" );
				lblNewLabel_1.setFont( SMALL_LABEL_FONT );

				alphaSlider = new DoubleJSlider( 0, 20 * scale, ( int ) ( params[ 5 ] * scale ), scale );

				alphaText = new JTextField( "" + params[ 5 ] );
				alphaText.setFont( TEXT_FIELD_FONT );

				link( alphaSlider, alphaText );
				alphaSlider.addChangeListener( step4ChangeListener );
				alphaText.addKeyListener( step4KeyListener );
			}
			{
				betaLabel = new JLabel( "\u03B2: positive laplacian magnitude prefactor" );
				betaLabel.setFont( SMALL_LABEL_FONT );

				betaSlider = new DoubleJSlider( 0, 20 * scale, ( int ) ( params[ 6 ] * scale ), scale );

				betaText = new JTextField();
				betaText.setFont( TEXT_FIELD_FONT );
				betaText.setText( "" + params[ 6 ] );

				link( betaSlider, betaText );
				betaSlider.addChangeListener( step4ChangeListener );
				betaText.addKeyListener( step4KeyListener );
			}
			{
				epsilonLabel = new JLabel( "\u03B5: negative hessian magnitude" );
				epsilonLabel.setFont( SMALL_LABEL_FONT );

				epsilonSlider = new DoubleJSlider( 0, 20 * scale, ( int ) ( scale * params[ 7 ] ), scale );

				epsilonText = new JTextField();
				epsilonText.setFont( TEXT_FIELD_FONT );
				epsilonText.setText( "" + params[ 7 ] );

				link( epsilonSlider, epsilonText );
				epsilonSlider.addChangeListener( step4ChangeListener );
				epsilonText.addKeyListener( step4KeyListener );
			}
			{
				deltaLabel = new JLabel( "\u03B4: derivatives sum scale" );
				deltaLabel.setFont( SMALL_LABEL_FONT );

				deltaText = new JTextField();
				deltaText.setFont( TEXT_FIELD_FONT );
				deltaText.setText( "" + params[ 8 ] );

				deltaSlider = new DoubleJSlider( 0, 5 * scale, ( int ) ( params[ 8 ] * scale ), scale );

				link( deltaSlider, deltaText );
				deltaSlider.addChangeListener( step4ChangeListener );
				deltaText.addKeyListener( step4KeyListener );
			}

			lblEquation = new JLabel( "<html>M = \u00BD ( 1 + <i>tanh</i> ( \u03B3 - ( \u03B1 G + \u03B2 L + \u03B5 H ) / \u03B4 ) )</html>" );
			lblEquation.setHorizontalAlignment( SwingConstants.CENTER );
			lblEquation.setFont( MEDIUM_LABEL_FONT );

			lblEstimatedTime = new JLabel( "Tune parameters to get a duration estimate" );
			lblEstimatedTime.setFont( SMALL_LABEL_FONT );
		}
		final GroupLayout gl_panelParams2 = new GroupLayout( panelParams2 );
		gl_panelParams2.setHorizontalGroup(
				gl_panelParams2.createParallelGroup( Alignment.LEADING )
						.addGroup( gl_panelParams2.createSequentialGroup()
								.addGroup( gl_panelParams2.createParallelGroup( Alignment.TRAILING )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addComponent( gammaSlider, GroupLayout.DEFAULT_SIZE, 297, Short.MAX_VALUE )
												.addPreferredGap( ComponentPlacement.RELATED )
												.addComponent( gammaText, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addComponent( alphaSlider, GroupLayout.DEFAULT_SIZE, 297, Short.MAX_VALUE )
												.addPreferredGap( ComponentPlacement.RELATED )
												.addComponent( alphaText, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addComponent( betaSlider, GroupLayout.DEFAULT_SIZE, 297, Short.MAX_VALUE )
												.addPreferredGap( ComponentPlacement.RELATED )
												.addComponent( betaText, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addComponent( epsilonSlider, GroupLayout.DEFAULT_SIZE, 297, Short.MAX_VALUE )
												.addPreferredGap( ComponentPlacement.RELATED )
												.addComponent( epsilonText, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addComponent( deltaSlider, GroupLayout.DEFAULT_SIZE, 297, Short.MAX_VALUE )
												.addPreferredGap( ComponentPlacement.RELATED )
												.addComponent( deltaText, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addComponent( lblParameterSet, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addGroup( gl_panelParams2.createParallelGroup( Alignment.TRAILING )
														.addComponent( lblEstimatedTime, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE )
														.addComponent( lblMasking, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE ) ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addComponent( gammeLabel, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addComponent( lblNewLabel_1, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addComponent( betaLabel, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addComponent( epsilonLabel, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 20 )
												.addComponent( deltaLabel, GroupLayout.DEFAULT_SIZE, 353, Short.MAX_VALUE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 10 )
												.addComponent( lblEquation, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE ) ) )
								.addContainerGap() )
				);
		gl_panelParams2.setVerticalGroup(
				gl_panelParams2.createParallelGroup( Alignment.LEADING )
						.addGroup( gl_panelParams2.createSequentialGroup()
								.addGroup( gl_panelParams2.createParallelGroup( Alignment.LEADING )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 11 )
												.addComponent( lblParameterSet, GroupLayout.PREFERRED_SIZE, 29, GroupLayout.PREFERRED_SIZE )
												.addGap( 31 )
												.addComponent( lblEstimatedTime, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE ) )
										.addGroup( gl_panelParams2.createSequentialGroup()
												.addGap( 51 )
												.addComponent( lblMasking, GroupLayout.PREFERRED_SIZE, 29, GroupLayout.PREFERRED_SIZE ) ) )
								.addGap( 12 )
								.addComponent( gammeLabel )
								.addGap( 11 )
								.addGroup( gl_panelParams2.createParallelGroup( Alignment.LEADING )
										.addComponent( gammaText, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE )
										.addComponent( gammaSlider, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE ) )
								.addGap( 11 )
								.addComponent( lblNewLabel_1 )
								.addGap( 11 )
								.addGroup( gl_panelParams2.createParallelGroup( Alignment.LEADING )
										.addComponent( alphaText, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE )
										.addComponent( alphaSlider, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE ) )
								.addGap( 11 )
								.addComponent( betaLabel )
								.addGap( 11 )
								.addGroup( gl_panelParams2.createParallelGroup( Alignment.LEADING )
										.addComponent( betaText, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE )
										.addComponent( betaSlider, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE ) )
								.addGap( 11 )
								.addComponent( epsilonLabel )
								.addGap( 11 )
								.addGroup( gl_panelParams2.createParallelGroup( Alignment.LEADING )
										.addComponent( epsilonText, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE )
										.addComponent( epsilonSlider, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE ) )
								.addGap( 11 )
								.addComponent( deltaLabel )
								.addGap( 11 )
								.addGroup( gl_panelParams2.createParallelGroup( Alignment.LEADING )
										.addComponent( deltaText, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE )
										.addComponent( deltaSlider, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE ) )
								.addGap( 23 )
								.addComponent( lblEquation, GroupLayout.PREFERRED_SIZE, 35, GroupLayout.PREFERRED_SIZE )
								.addContainerGap( 106, Short.MAX_VALUE ) )
				);
		panelParams2.setLayout( gl_panelParams2 );

		{
			final JPanel panelRun = new JPanel();
			tabbedPane.addTab( "Run", null, panelRun, null );

			final JLabel lblLaunchComputation = new JLabel( "Launch computation" );
			lblLaunchComputation.setFont( BIG_LABEL_FONT );
			lblLaunchComputation.setHorizontalAlignment( SwingConstants.CENTER );

			lblEstimatedTime_1 = new JLabel( "Tune parameters to get a duration estimate" );
			lblEstimatedTime_1.setFont( SMALL_LABEL_FONT );

			btnGo = new JButton( "Go!" );
			btnGo.setFont( MEDIUM_LABEL_FONT );
			btnGo.setIcon( new ImageIcon( CwntGui.class.getResource( "resources/plugin_go.png" ) ) );
			btnGo.addActionListener( new ActionListener()
			{
				@Override
				public void actionPerformed( final ActionEvent e )
				{
					fireEvent( GO_BUTTON_PRESSED );
				}
			} );

			chckbxGenLabels = new JCheckBox( "Show label image." );
			chckbxGenLabels.setFont( SMALL_LABEL_FONT );
			chckbxGenLabels.setSelected( false );

			chckbxShowColoredLabel = new JCheckBox( "Show colored label image." );
			chckbxShowColoredLabel.setFont( SMALL_LABEL_FONT );
			chckbxShowColoredLabel.setSelected( true );

			progressBar = new JProgressBar( 0, 100 );
			progressBar.setStringPainted( true );
			progressBar.setFont( FONT );

			chckbxSplitLargeNuclei = new JCheckBox( "Use Kmeans to split large nuclei." );
			chckbxSplitLargeNuclei.setFont( SMALL_LABEL_FONT );
			chckbxSplitLargeNuclei.setSelected( true );
			chckbxSplitLargeNuclei.setToolTipText( "<html>"
					+ "Sub-optimal crown mask and/or large extension in Z <br>"
					+ "might generate artificially connected large nuclei."
					+ "<p>"
					+ "This extra step splits them using Kmeans++ clustering <br>"
					+ "algorithm based on their volume compactness."
					+ "</html>" );

			final GroupLayout gl_panelRun = new GroupLayout( panelRun );
			gl_panelRun.setHorizontalGroup(
					gl_panelRun.createParallelGroup( Alignment.LEADING )
							.addGroup( gl_panelRun.createSequentialGroup()
									.addGap( 10 )
									.addGroup( gl_panelRun.createParallelGroup( Alignment.LEADING )
											.addComponent( lblLaunchComputation, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE )
											.addComponent( lblEstimatedTime_1, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE ) )
									.addContainerGap() )
							.addGroup( gl_panelRun.createSequentialGroup()
									.addContainerGap()
									.addComponent( chckbxShowColoredLabel, GroupLayout.DEFAULT_SIZE, 367, Short.MAX_VALUE )
									.addContainerGap() )
							.addGroup( gl_panelRun.createSequentialGroup()
									.addContainerGap()
									.addComponent( progressBar, GroupLayout.DEFAULT_SIZE, 367, Short.MAX_VALUE )
									.addContainerGap() )
							.addGroup( gl_panelRun.createSequentialGroup()
									.addContainerGap( 273, Short.MAX_VALUE )
									.addComponent( btnGo, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE )
									.addContainerGap() )
							.addGroup( Alignment.TRAILING, gl_panelRun.createSequentialGroup()
									.addContainerGap()
									.addGroup( gl_panelRun.createParallelGroup( Alignment.TRAILING )
											.addComponent( chckbxSplitLargeNuclei, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE )
											.addComponent( chckbxGenLabels, GroupLayout.DEFAULT_SIZE, 363, Short.MAX_VALUE ) )
									.addGap( 10 ) )
					);
			gl_panelRun.setVerticalGroup(
					gl_panelRun.createParallelGroup( Alignment.LEADING )
							.addGroup( gl_panelRun.createSequentialGroup()
									.addGap( 11 )
									.addComponent( lblLaunchComputation, GroupLayout.PREFERRED_SIZE, 31, GroupLayout.PREFERRED_SIZE )
									.addGap( 29 )
									.addComponent( lblEstimatedTime_1, GroupLayout.PREFERRED_SIZE, 23, GroupLayout.PREFERRED_SIZE )
									.addGap( 39 )
									.addComponent( chckbxSplitLargeNuclei )
									.addPreferredGap( ComponentPlacement.RELATED )
									.addComponent( chckbxGenLabels )
									.addPreferredGap( ComponentPlacement.RELATED )
									.addComponent( chckbxShowColoredLabel )
									.addPreferredGap( ComponentPlacement.RELATED, 258, Short.MAX_VALUE )
									.addComponent( btnGo, GroupLayout.PREFERRED_SIZE, 50, GroupLayout.PREFERRED_SIZE )
									.addPreferredGap( ComponentPlacement.RELATED )
									.addComponent( progressBar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE )
									.addContainerGap() )
					);
			panelRun.setLayout( gl_panelRun );

		}
	}

	private void link( final DoubleJSlider slider, final JTextField text )
	{
		slider.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				text.setText( df2d.format( slider.getScaledValue() ) );
			}
		} );
		text.addKeyListener( new KeyAdapter()
		{
			@Override
			public void keyReleased( final KeyEvent ke )
			{
				final String typed = text.getText();
				if ( !typed.matches( "\\d+(\\.\\d+)?" ) ) {
				return;
				}
				final double value = Double.parseDouble( typed ) * slider.scale;
				slider.setValue( ( int ) value );
			}
		} );
	}

	private class GuiLogger extends Logger
	{
		private final Logger ijl = Logger.IJ_LOGGER;

		@Override
		public void log( final String message, final Color color )
		{
			ijl.log( message, color );
		}

		@Override
		public void error( final String message )
		{
			ijl.error( message );
		}

		@Override
		public void setProgress( final double val )
		{
			SwingUtilities.invokeLater( new Runnable()
			{
				@Override
				public void run()
				{
					progressBar.setValue( ( int ) ( 100 * val ) );
				}
			} );
		}

		@Override
		public void setStatus( final String status )
		{
			SwingUtilities.invokeLater( new Runnable()
			{
				@Override
				public void run()
				{
					progressBar.setString( status );
				}
			} );
		}
	}
}
