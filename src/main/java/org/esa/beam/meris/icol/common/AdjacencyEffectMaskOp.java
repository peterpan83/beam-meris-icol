/*
 * $Id: AeMaskOp.java,v 1.5 2007/05/10 13:04:27 marcoz Exp $
 *
 * Copyright (C) 2007 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.meris.icol.common;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.RectangleExtender;

import java.awt.Rectangle;
import java.awt.geom.Area;


/**
 * Operator for computation of the mask to be used for AE correction.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "AEMask",
                  version = "2.9.5",
                  internal = true,
                  authors = "Marco Zühlke, Olaf Danne",
                  copyright = "(c) 2010 by Brockmann Consult",
                  description = "Adjacency mask computation.")
public class AdjacencyEffectMaskOp extends Operator {

    public static final String AE_MASK_RAYLEIGH = "ae_mask_rayleigh";
    public static final String AE_MASK_AEROSOL = "ae_mask_aerosol";

    private static final int RR_WIDTH = 25;
    private static final int FR_WIDTH = 100;

    private RectangleExtender rectCalculator;
    private Rectangle relevantRect;
    private int aeWidth;
    private Band isLandBand;
    private Band isCoastlineBand;

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @SourceProduct(alias = "land")
    private Product landProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter
    private String landExpression;
    @Parameter
    private String coastlineExpression;

    @Parameter(defaultValue = "COASTAL_OCEAN", valueSet = {"EVERYWHERE", "COASTAL_ZONE", "COASTAL_OCEAN", "OCEAN"})
    private AeArea aeArea;
    @Parameter
    private boolean reshapedConvolution;
    @Parameter
    private int correctionMode;


    @Override
    public void initialize() throws OperatorException {
        targetProduct = OperatorUtils.createCompatibleProduct(sourceProduct, "ae_mask_" + sourceProduct.getName(),
                                                              "AEMASK");

        Band maskBand = null;
        double sourceExtendReduction = 1.0;
        if (correctionMode == IcolConstants.AE_CORRECTION_MODE_RAYLEIGH) {
            // Rayleigh
            if (reshapedConvolution) {
                sourceExtendReduction = (double) IcolConstants.DEFAULT_AE_DISTANCE / (double) IcolConstants.RAYLEIGH_AE_DISTANCE;
            } else {
                sourceExtendReduction = 1.0;
            }
            maskBand = targetProduct.addBand(AE_MASK_RAYLEIGH, ProductData.TYPE_INT8);
        } else if (correctionMode == IcolConstants.AE_CORRECTION_MODE_AEROSOL) {
            // aerosol
            if (reshapedConvolution) {
                sourceExtendReduction = (double) IcolConstants.DEFAULT_AE_DISTANCE / (double) IcolConstants.AEROSOL_AE_DISTANCE;
            } else {
                sourceExtendReduction = 1.0;
            }
            maskBand = targetProduct.addBand(AE_MASK_AEROSOL, ProductData.TYPE_INT8);
        }

        final String productType = sourceProduct.getProductType();
        if (productType.indexOf("_RR") > -1) {
            aeWidth = (int) (RR_WIDTH / sourceExtendReduction);
        } else {
            aeWidth = (int) (FR_WIDTH / sourceExtendReduction);
        }

        FlagCoding flagCoding = createFlagCoding();
        maskBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        BandMathsOp bandArithmeticOp1 = BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp1.getTargetProduct().getBandAt(0);

        if (coastlineExpression != null && !coastlineExpression.isEmpty()) {
            BandMathsOp bandArithmeticOp2 = BandMathsOp.createBooleanExpressionBand(coastlineExpression, sourceProduct);
            isCoastlineBand = bandArithmeticOp2.getTargetProduct().getBandAt(0);
        }

        // todo: the following works for nested convolution - check if this is sufficient as 'edge processing' (proposal 3.2.1.5)
        int sourceWidth = sourceProduct.getSceneRasterWidth();
        int sourceHeight = sourceProduct.getSceneRasterHeight();
        if (reshapedConvolution) {
            relevantRect = new Rectangle(0, 0, sourceWidth, sourceHeight);
        } else {
            // (AE algorithm is not applied in this case)
            if (sourceWidth - 2 * aeWidth < 0 || sourceHeight - 2 * aeWidth < 0) {
                throw new OperatorException("Product is too small to apply AE correction - must be at least " +
                                            2 * aeWidth + "x" + 2 * aeWidth + " pixel.");
            }
            relevantRect = new Rectangle(aeWidth, aeWidth,
                                         sourceWidth - 2 * aeWidth,
                                         sourceHeight - 2 * aeWidth);
        }
        rectCalculator = new RectangleExtender(new Rectangle(sourceWidth, sourceHeight), aeWidth, aeWidth);
    }

    private FlagCoding createFlagCoding() {
        FlagCoding flagCoding = null;
        if (correctionMode == IcolConstants.AE_CORRECTION_MODE_RAYLEIGH) {
            flagCoding = new FlagCoding(AE_MASK_RAYLEIGH);
        } else if (correctionMode == IcolConstants.AE_CORRECTION_MODE_AEROSOL) {
            flagCoding = new FlagCoding(AE_MASK_AEROSOL);
        }
        flagCoding.addFlag("aep", BitSetter.setFlag(0, 0), null);
        return flagCoding;
    }

    @Override
    public void computeTile(Band band, Tile aeMask, ProgressMonitor pm) throws OperatorException {

        Rectangle targetRect = aeMask.getRectangle();
        Rectangle sourceRect = rectCalculator.extend(targetRect);
        Rectangle relevantTragetRect = targetRect.intersection(relevantRect);
        pm.beginTask("Processing frame...", sourceRect.height + relevantTragetRect.height + 3);
        try {
            Tile isLand = getSourceTile(isLandBand, sourceRect);
            Tile isCoastline = null;
            if (isCoastlineBand != null) {
                isCoastline = getSourceTile(isCoastlineBand, sourceRect);
            }
            Tile sza = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME),
                                     sourceRect);

            Tile detectorIndexTile = null;
            if (sourceProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME) != null) {
                detectorIndexTile = getSourceTile(sourceProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME),
                                                  sourceRect);
            }

            Area coastalArea = computeCoastalArea(pm, sourceRect, isLand, isCoastline);
            boolean correctOverLand = aeArea.correctOverLand();
            boolean correctInCoastalAreas = aeArea.correctCoastalArea();
            // todo: over land, apply AE algorithm everywhere except for cloud pixels.
            // even if land pixel is far away from water
            for (int y = relevantTragetRect.y; y < relevantTragetRect.y + relevantTragetRect.height; y++) {
                for (int x = relevantTragetRect.x; x < relevantTragetRect.x + relevantTragetRect.width; x++) {
                    if (Math.abs(sza.getSampleFloat(x, y)) > 80.0) {
                        // we do not correct AE for sun zeniths > 80 deg because of limitation in aerosol scattering
                        // functions (PM4, 2010/03/04)
                        aeMask.setSample(x, y, 0);
                    } else if (detectorIndexTile != null && isAtInvalidEdge(x, y, relevantTragetRect,
                                                                            detectorIndexTile)) {
                        // In MERIS N1, we often see vertical stripes of invalid (corrupt?) pixels with zero radiances
                        // at the left and right edges of the images leading to artifacts after AE correction.
                        // To avoid this, we do not apply AE correction here
                        aeMask.setSample(x, y, 0);
                    } else {
                        // if 'correctOverLand',  compute for both ocean and land ...
                        if (correctOverLand) {
                            // if 'correctInCoastalAreas',  check if pixel is in coastal area...
                            if (correctInCoastalAreas && !coastalArea.contains(x, y)) {
                                aeMask.setSample(x, y, 0);
                            } else {
                                aeMask.setSample(x, y, 1);
                            }
                        } else {
                            if (isLand.getSampleBoolean(x, y) ||
                                (correctInCoastalAreas && !coastalArea.contains(x, y))) {
                                aeMask.setSample(x, y, 0);
                            } else {
                                aeMask.setSample(x, y, 1);
                            }
                        }
                    }
                }
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private boolean isAtInvalidEdge(int x, int y, Rectangle relevantTragetRect, Tile detectorIndexTile) {
        final int firstValidIndex = getFirstValidIndex(y, relevantTragetRect, detectorIndexTile);
        boolean invalidLeftEdgePixel = (firstValidIndex != -1 && x < firstValidIndex + aeWidth);

        final int lastValidIndex = getLastValidIndex(y, relevantTragetRect, detectorIndexTile);
        boolean invalidRightEdgePixel = (lastValidIndex != -1 && x > lastValidIndex - aeWidth);

        return invalidLeftEdgePixel || invalidRightEdgePixel;
    }

    private int getFirstValidIndex(int y, Rectangle rectangle, Tile detectorIndexTile) {
        int firstValidIndex = -1;
        // check if transition from invalid to valid happens in this tile
        if (detectorIndexTile.getSampleInt(rectangle.x, y) == -1 &&
            detectorIndexTile.getSampleInt(rectangle.x + rectangle.width - 1, y) != -1) {
            // determine first valid pixel in row
            for (int x = rectangle.x; x < rectangle.x + rectangle.width - 1; x++) {
                if (detectorIndexTile.getSampleInt(x, y) == -1 && detectorIndexTile.getSampleInt(x + 1,
                                                                                                 y) != -1) {
                    firstValidIndex = x + 1;
                    break;
                }
            }
        }

        return firstValidIndex;
    }

    private int getLastValidIndex(int y, Rectangle rectangle, Tile detectorIndexTile) {
        int lastValidIndex = -1;

        // check if transition from valid to invalid happens in this tile
        if (detectorIndexTile.getSampleInt(rectangle.x, y) != -1 &&
            detectorIndexTile.getSampleInt(rectangle.x + rectangle.width - 1, y) == -1) {
            // determine first valid pixel in row
            for (int x = rectangle.x; x < rectangle.x + rectangle.width - 1; x++) {
                if (detectorIndexTile.getSampleInt(x, y) != -1 && detectorIndexTile.getSampleInt(x + 1, y) == -1) {
                    lastValidIndex = x;
                    break;
                }
            }
        }

        return lastValidIndex;
    }


    private Area computeCoastalArea(ProgressMonitor pm, Rectangle sourceRect, Tile land, Tile coastline) {
        Rectangle box = new Rectangle();
        Area coastalArea = new Area();
        for (int y = sourceRect.y; y < sourceRect.y + sourceRect.height; y++) {
            for (int x = sourceRect.x; x < sourceRect.x + sourceRect.width; x++) {
                if (isCoastline(land, coastline, x, y)) {
                    box.setBounds(x - aeWidth, y - aeWidth, 2 * aeWidth, 2 * aeWidth);
                    Area area2 = new Area(box);
                    coastalArea.add(area2);
                }
            }
            pm.worked(1);
        }
        return coastalArea;
    }

    private boolean isCoastline(Tile isLandTile, Tile coastline, int x, int y) {
        if (coastline != null) {
            return coastline.getSampleBoolean(x, y);
        }
        if (LandsatUtils.isCoordinatesOutOfBounds(x - 1, y - 1, isLandTile) ||
            LandsatUtils.isCoordinatesOutOfBounds(x + 1, y + 1, isLandTile)) {
            return false;
        }
        return isCoastline(isLandTile, x, y);
    }

    private boolean isCoastline(Tile isLandTile, int x, int y) {
        // use this: isLandTile.getRectangle() !!!
        final boolean isLandMiddleLeft = isLandTile.getSampleBoolean(x - 1, y);
        final boolean isLandMiddleRight = isLandTile.getSampleBoolean(x + 1, y);

        if ((isLandMiddleLeft && !isLandMiddleRight) || (isLandMiddleRight && !isLandMiddleLeft)) {
            return true;
        }

        final boolean isLandBottomMiddle = isLandTile.getSampleBoolean(x, y - 1);
        final boolean isLandTopMiddle = isLandTile.getSampleBoolean(x, y + 1);

        if ((isLandBottomMiddle && !isLandTopMiddle) || (isLandTopMiddle && !isLandBottomMiddle)) {
            return true;
        }

        final boolean isLandBottomLeft = isLandTile.getSampleBoolean(x - 1, y - 1);
        final boolean isLandTopRight = isLandTile.getSampleBoolean(x + 1, y + 1);

        if ((isLandBottomLeft && !isLandTopRight) || (isLandTopRight && !isLandBottomLeft)) {
            return true;
        }

        final boolean isLandLowerRight = isLandTile.getSampleBoolean(x + 1, y - 1);
        final boolean isLandUpperLeft = isLandTile.getSampleBoolean(x - 1, y + 1);
        if ((isLandLowerRight && !isLandUpperLeft) || (isLandUpperLeft && !isLandLowerRight)) {
            return true;
        }

        return false;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AdjacencyEffectMaskOp.class);
        }
    }
}