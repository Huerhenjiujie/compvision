package mpicbg.stitching;

import ij.IJ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.Vector;

import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;


public class GlobalOptimization 
{
	
	public static ArrayList< ImagePlusTimePoint > optimize( final Vector< ComparePair > pairs, final ImagePlusTimePoint fixedImage, final StitchingParameters params )
	{
		boolean redo;
		TileConfigurationStitching tc;
		//ArrayList< Tile< ? > > tiles1 = new ArrayList< Tile< ? > >();
		do
		{
			redo = false;
			ArrayList< Tile< ? > > tiles = new ArrayList< Tile< ? > >();
			//tiles1.clear();
			
			for ( final ComparePair pair : pairs )
			{
				if ( pair.getCrossCorrelation() >= params.regThreshold && pair.getIsValidOverlap() )
				{
					Tile t1 = pair.getTile1();
					Tile t2 = pair.getTile2();
					
					Point p1, p2;
					
					if ( params.dimensionality == 3 )
					{
						// the transformations that map each tile into the relative global coordinate system (that's why the "-")
						p1 = new Point( new float[]{ 0,0,0 } );
						
						if ( params.ignoreZStage )
							p2 = new Point( new float[]{ -pair.getRelativeShift()[ 0 ], -pair.getRelativeShift()[ 1 ], 0 } );
						else
							p2 = new Point( new float[]{ -pair.getRelativeShift()[ 0 ], -pair.getRelativeShift()[ 1 ], -pair.getRelativeShift()[ 2 ] } );
					}
					else 
					{
						p1 = new Point( new float[]{ 0, 0 } );
						p2 = new Point( new float[]{ -pair.getRelativeShift()[ 0 ], -pair.getRelativeShift()[ 1 ] } );						
					}
					
					t1.addMatch( new PointMatchStitching( p1, p2, (float)pair.getCrossCorrelation(), pair ) );
					t2.addMatch( new PointMatchStitching( p2, p1, (float)pair.getCrossCorrelation(), pair ) );
					t1.addConnectedTile( t2 );
					t2.addConnectedTile( t1 );
					
					if (!tiles.contains(t1))
						tiles.add( t1 );
	
					if (!tiles.contains(t2))
						tiles.add( t2 );			
					
					pair.setIsValidOverlap( true );
				}
				else
				{
					pair.setIsValidOverlap( false );
					
					/*Tile t1 = pair.getTile1();
					Tile t2 = pair.getTile2();
					
					if (!tiles1.contains(t1))
						tiles1.add( t1 );
	
					if (!tiles1.contains(t2))
						tiles1.add( t2 );*/
				}
			}
			
			if ( tiles.size() == 0 )
			{
				
				if ( params.dimensionality == 3 )
				{
					IJ.log( "Error: No correlated tiles found, setting the first tile to (0, 0, 0)." );
					TranslationModel3D model = (TranslationModel3D)fixedImage.getModel();
					model.set( 0, 0, 0 );
				}
				else
				{
					IJ.log( "Error: No correlated tiles found, setting the first tile to (0, 0)." );
					TranslationModel2D model = (TranslationModel2D)fixedImage.getModel();
					model.set( 0, 0 );					
				}
				
				ArrayList< ImagePlusTimePoint > imageInformationList = new ArrayList< ImagePlusTimePoint >();
				imageInformationList.add( fixedImage );
				
				IJ.log(" number of tiles = " + imageInformationList.size() );
				
				return imageInformationList;
			}						
			
			/*
			// trash everything but the largest graph			
			final ArrayList< Set< Tile< ? > > > graphs = Tile.identifyConnectedGraphs( tiles );
			IJ.log( "Number of tile graphs = " + graphs.size() );
			
			int largestGraphSize = 0;
			int largestGraphId = -1;
			
			for ( int i = 0; i < graphs.size(); ++i )
			{
				IJ.log( "Graph " + i + ": size = " + graphs.get( i ).size() );
				if ( graphs.get( i ).size() > largestGraphSize )
				{
					largestGraphSize = graphs.get( i ).size();
					largestGraphId = i;
				}
			}
			
			ArrayList< Tile > largestGraph = new ArrayList< Tile >();
			largestGraph.addAll( graphs.get( largestGraphId ) );
			*/
			
			tc = new TileConfigurationStitching();
			tc.addTiles( tiles );
			
			// find a useful fixed tile
			if ( fixedImage.getConnectedTiles().size() > 0 )
			{
				tc.fixTile( fixedImage );
			}
			else
			{
				for ( int i = 0; i < tiles.size(); ++i )
					if ( tiles.get( i ).getConnectedTiles().size() > 0 )
					{
						tc.fixTile( tiles.get( i ) );
						break;
					}
			}
			//IJ.log(" tiles size =" + tiles.size());
			//IJ.log(" tc.getTiles() size =" + tc.getTiles().size());

			try
			{
				tc.preAlign();
				tc.optimize( 10, 1000, 200 );

				double avgError = tc.getError();
				double maxError = tc.getMaxError();				

				if ( ( ( avgError*params.relativeThreshold < maxError && maxError > 0.95 ) || avgError > params.absoluteThreshold ) )
				{
					float longestDisplacement = 0;
					PointMatch worstMatch = null;

					// new way of finding biggest error to look for the largest displacement
					for ( final Tile t : tc.getTiles() )
					{
						for ( PointMatch p :  (Set< PointMatch >)t.getMatches() )
						{
							if ( p.getDistance() > longestDisplacement )
							{
								longestDisplacement = p.getDistance();
								worstMatch = p;
							}
						}
					}

					/*
					Tile worstTile = tc.getWorstTile();
					Set< PointMatch > matches = worstTile.getMatches();
					
					float longestDisplacement = 0;
					PointMatch worstMatch = null;
					
					//IJ.log( "worstTile: " + ((ImagePlusTimePoint)worstTile).getImagePlus().getTitle() );

					for (PointMatch p : matches)
					{
						//IJ.log( "distance: " + p.getDistance() + " to " + ((PointMatchStitching)p).getPair().getImagePlus2().getTitle() );

						if (p.getDistance() > longestDisplacement)
						{
							longestDisplacement = p.getDistance();
							worstMatch = p;
						}
					}
					*/
					final ComparePair pair = ((PointMatchStitching)worstMatch).getPair();
					
					IJ.log( "Identified link between " + pair.getImagePlus1().getTitle() + "[" + pair.getTile1().getTimePoint() + "] and " + 
							pair.getImagePlus2().getTitle() + "[" + pair.getTile2().getTimePoint() + "] (R=" + pair.getCrossCorrelation() +") to be bad. Reoptimizing.");
					
					((PointMatchStitching)worstMatch).getPair().setIsValidOverlap( false );
					redo = true;
					
					for ( final Tile< ? > t : tiles )
					{
						t.getConnectedTiles().clear();
						t.getMatches().clear();
					}
				}
			}
			catch ( Exception e )
			{ 
				IJ.log( "Cannot compute global optimization: " + e ); 
				e.printStackTrace(); 
			}
		}
		while(redo);
		
		
		ArrayList< Tile< ? > > tiles1 = new ArrayList< Tile< ? > >();
		for ( final ComparePair pair : pairs )
		{			
			if(!(tc.getTiles()).contains((ImagePlusTimePoint)(pair.getTile1())) && !tiles1.contains(pair.getTile1()))
				tiles1.add(pair.getTile1());
			if(!(tc.getTiles()).contains((ImagePlusTimePoint)(pair.getTile2())) && !tiles1.contains(pair.getTile2()))
				tiles1.add(pair.getTile2());
		}
		
		// create a list of image informations containing their positions			
		ArrayList< ImagePlusTimePoint > imageInformationList = new ArrayList< ImagePlusTimePoint >();
		for ( Tile< ? > t : tc.getTiles() )
			imageInformationList.add( (ImagePlusTimePoint)t );
		
		
		for ( Tile< ? > t : tiles1)
			imageInformationList.add( (ImagePlusTimePoint)t );
		
		Collections.sort( imageInformationList );
		
		for ( Tile< ? > t : tiles1)
			if(imageInformationList.contains((ImagePlusTimePoint)t))
				imageInformationList.set(imageInformationList.indexOf((ImagePlusTimePoint)t), null);
		
		return imageInformationList;
	}
}
