/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package sun.java2d.marlin;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.FastPath2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.lang.ref.Reference;
import java.util.concurrent.ConcurrentLinkedQueue;
import static sun.java2d.marlin.MarlinUtils.logInfo;
import sun.awt.geom.PathConsumer2D;
import sun.java2d.pipe.AATileGenerator;
import sun.java2d.pipe.Region;
import sun.java2d.pipe.RenderingEngine;

/**
 * Marlin RendererEngine implementation (derived from Pisces)
 */
public class MarlinRenderingEngine extends RenderingEngine implements MarlinConst {
    private static enum NormMode {OFF, ON_NO_AA, ON_WITH_AA}

    /**
     * Create a widened path as specified by the parameters.
     * <p>
     * The specified {@code src} {@link Shape} is widened according
     * to the specified attribute parameters as per the
     * {@link BasicStroke} specification.
     *
     * @param src the source path to be widened
     * @param width the width of the widened path as per {@code BasicStroke}
     * @param caps the end cap decorations as per {@code BasicStroke}
     * @param join the segment join decorations as per {@code BasicStroke}
     * @param miterlimit the miter limit as per {@code BasicStroke}
     * @param dashes the dash length array as per {@code BasicStroke}
     * @param dashphase the initial dash phase as per {@code BasicStroke}
     * @return the widened path stored in a new {@code Shape} object
     * @since 1.7
     */
    @Override
    public Shape createStrokedShape(Shape src,
                                    float width,
                                    int caps,
                                    int join,
                                    float miterlimit,
                                    float dashes[],
                                    float dashphase)
    {
        final RendererContext rdrCtx = getRendererContext();

        // initialize a large copyable FastPath2D to avoid a lot of array growing:
        final FastPath2D p2d = (rdrCtx.p2d == null) ? (rdrCtx.p2d = new FastPath2D(INITIAL_MEDIUM_ARRAY)) : rdrCtx.p2d;
        // reset
        p2d.reset();
        
        strokeTo(rdrCtx,
                 src,
                 null,
                 width,
                 NormMode.OFF, /* LBO: should use ON_WITH_AA to be more precise ? */
                 caps,
                 join,
                 miterlimit,
                 dashes,
                 dashphase,
                 rdrCtx.transformerPC2D.wrapPath2d(p2d)
                );
        
        /* Perform Path2D copy efficiently and trim */
        final Path2D path = p2d.trimmedCopy();
        
        returnRendererContext(rdrCtx);
        
        return path;
    }

    /**
     * Sends the geometry for a widened path as specified by the parameters
     * to the specified consumer.
     * <p>
     * The specified {@code src} {@link Shape} is widened according
     * to the parameters specified by the {@link BasicStroke} object.
     * Adjustments are made to the path as appropriate for the
     * {@link VALUE_STROKE_NORMALIZE} hint if the {@code normalize}
     * boolean parameter is true.
     * Adjustments are made to the path as appropriate for the
     * {@link VALUE_ANTIALIAS_ON} hint if the {@code antialias}
     * boolean parameter is true.
     * <p>
     * The geometry of the widened path is forwarded to the indicated
     * {@link PathConsumer2D} object as it is calculated.
     *
     * @param src the source path to be widened
     * @param bs the {@code BasicSroke} object specifying the
     *           decorations to be applied to the widened path
     * @param normalize indicates whether stroke normalization should
     *                  be applied
     * @param antialias indicates whether or not adjustments appropriate
     *                  to antialiased rendering should be applied
     * @param consumer the {@code PathConsumer2D} instance to forward
     *                 the widened geometry to
     * @since 1.7
     */
    @Override
    public void strokeTo(Shape src,
                         AffineTransform at,
                         BasicStroke bs,
                         boolean thin,
                         boolean normalize,
                         boolean antialias,
                         final PathConsumer2D consumer)
    {
        NormMode norm = (normalize) ?
                ((antialias) ? NormMode.ON_WITH_AA : NormMode.ON_NO_AA)
                : NormMode.OFF;

        final RendererContext rdrCtx = getRendererContext();

        strokeTo(rdrCtx, src, at, bs, thin, norm, antialias, consumer);

        returnRendererContext(rdrCtx);
    }

    final void strokeTo(final RendererContext rdrCtx, 
                        Shape src,
                        AffineTransform at,
                        BasicStroke bs,
                        boolean thin,
                        NormMode normalize,
                        boolean antialias,
                        PathConsumer2D pc2d)
    {
        float lw;
        if (thin) {
            if (antialias) {
                lw = userSpaceLineWidth(at, 0.5f);
            } else {
                lw = userSpaceLineWidth(at, 1.0f);
            }
        } else {
            lw = bs.getLineWidth();
        }
        strokeTo(rdrCtx, 
                 src,
                 at,
                 lw,
                 normalize,
                 bs.getEndCap(),
                 bs.getLineJoin(),
                 bs.getMiterLimit(),
                 bs.getDashArray(),
                 bs.getDashPhase(),
                 pc2d);
    }

    private final float userSpaceLineWidth(AffineTransform at, float lw) {

        float widthScale;

        if (at == null) {
            widthScale = 1.0f;
        } else if ((at.getType() & (AffineTransform.TYPE_GENERAL_TRANSFORM  |
                                    AffineTransform.TYPE_GENERAL_SCALE)) != 0) {
            widthScale = (float)Math.sqrt(at.getDeterminant());
        } else {
            /* First calculate the "maximum scale" of this transform. */
            double A = at.getScaleX();       // m00
            double C = at.getShearX();       // m01
            double B = at.getShearY();       // m10
            double D = at.getScaleY();       // m11

            /*
             * Given a 2 x 2 affine matrix [ A B ] such that
             *                             [ C D ]
             * v' = [x' y'] = [Ax + Cy, Bx + Dy], we want to
             * find the maximum magnitude (norm) of the vector v'
             * with the constraint (x^2 + y^2 = 1).
             * The equation to maximize is
             *     |v'| = sqrt((Ax+Cy)^2+(Bx+Dy)^2)
             * or  |v'| = sqrt((AA+BB)x^2 + 2(AC+BD)xy + (CC+DD)y^2).
             * Since sqrt is monotonic we can maximize |v'|^2
             * instead and plug in the substitution y = sqrt(1 - x^2).
             * Trigonometric equalities can then be used to get
             * rid of most of the sqrt terms.
             */

            double EA = A*A + B*B;          // x^2 coefficient
            double EB = 2*(A*C + B*D);      // xy coefficient
            double EC = C*C + D*D;          // y^2 coefficient

            /*
             * There is a lot of calculus omitted here.
             *
             * Conceptually, in the interests of understanding the
             * terms that the calculus produced we can consider
             * that EA and EC end up providing the lengths along
             * the major axes and the hypot term ends up being an
             * adjustment for the additional length along the off-axis
             * angle of rotated or sheared ellipses as well as an
             * adjustment for the fact that the equation below
             * averages the two major axis lengths.  (Notice that
             * the hypot term contains a part which resolves to the
             * difference of these two axis lengths in the absence
             * of rotation.)
             *
             * In the calculus, the ratio of the EB and (EA-EC) terms
             * ends up being the tangent of 2*theta where theta is
             * the angle that the long axis of the ellipse makes
             * with the horizontal axis.  Thus, this equation is
             * calculating the length of the hypotenuse of a triangle
             * along that axis.
             */

            double hypot = Math.sqrt(EB*EB + (EA-EC)*(EA-EC));
            /* sqrt omitted, compare to squared limits below. */
            double widthsquared = ((EA + EC + hypot)/2.0);

            widthScale = (float)Math.sqrt(widthsquared);
        }

        return (lw / widthScale);
    }

    final void strokeTo(final RendererContext rdrCtx, 
                        Shape src,
                        AffineTransform at,
                        float width,
                        NormMode normalize,
                        int caps,
                        int join,
                        float miterlimit,
                        float dashes[],
                        float dashphase,
                        PathConsumer2D pc2d)
    {
        // We use strokerat and outat so that in Stroker and Dasher we can work only
        // with the pre-transformation coordinates. This will repeat a lot of
        // computations done in the path iterator, but the alternative is to
        // work with transformed paths and compute untransformed coordinates
        // as needed. This would be faster but I do not think the complexity
        // of working with both untransformed and transformed coordinates in
        // the same code is worth it.
        // However, if a path's width is constant after a transformation,
        // we can skip all this untransforming.

        // If normalization is off we save some transformations by not
        // transforming the input to pisces. Instead, we apply the
        // transformation after the path processing has been done.
        // We can't do this if normalization is on, because it isn't a good
        // idea to normalize before the transformation is applied.
        AffineTransform strokerat = null;
        AffineTransform outat = null;

        PathIterator pi;
        int dashLen = -1;
        boolean recycleDashes = false;

        if (at != null && !at.isIdentity()) {
            final double a = at.getScaleX();
            final double b = at.getShearX();
            final double c = at.getShearY();
            final double d = at.getScaleY();
            final double det = a * d - c * b;
            if (Math.abs(det) <= 2d * Float.MIN_VALUE) {
                // this rendering engine takes one dimensional curves and turns
                // them into 2D shapes by giving them width.
                // However, if everything is to be passed through a singular
                // transformation, these 2D shapes will be squashed down to 1D
                // again so, nothing can be drawn.

                // Every path needs an initial moveTo and a pathDone. If these
                // are not there this causes a SIGSEGV in libawt.so (at the time
                // of writing of this comment (September 16, 2010)). Actually,
                // I am not sure if the moveTo is necessary to avoid the SIGSEGV
                // but the pathDone is definitely needed.
                pc2d.moveTo(0f, 0f);
                pc2d.pathDone();
                return;
            }

            // If the transform is a constant multiple of an orthogonal transformation
            // then every length is just multiplied by a constant, so we just
            // need to transform input paths to stroker and tell stroker
            // the scaled width. This condition is satisfied if
            // a*b == -c*d && a*a+c*c == b*b+d*d. In the actual check below, we
            // leave a bit of room for error.
            if (nearZero(a*b + c*d, 2d) && nearZero(a*a+c*c - (b*b+d*d), 2d)) {
                final float scale = (float) Math.sqrt(a*a + c*c);
                // TODO: keep scale factor to compute transformed clip's bounding box
                
                if (dashes != null) {
                    // LBO: copy into recyclable array:
                    recycleDashes = true;
                    dashLen = dashes.length;
                    
                    // LBO: use dashes_initial if large enough
                    final float[] newDashes = (dashLen <= INITIAL_ARRAY) ? 
                            rdrCtx.dasher.dashes_initial : rdrCtx.getFloatArray(dashLen);
                    
                    System.arraycopy(dashes, 0, newDashes, 0, dashLen);
                    dashes = newDashes;
                    for (int i = 0; i < dashLen; i++) {
                        dashes[i] = scale * dashes[i];
                    }
                    dashphase = scale * dashphase;
                }
                width = scale * width;
                pi = src.getPathIterator(at);
                if (normalize != NormMode.OFF) {
                    pi = rdrCtx.npIterator.init(pi, normalize);
                }
                // by now strokerat == null && outat == null. Input paths to
                // stroker (and maybe dasher) will have the full transform at
                // applied to them and nothing will happen to the output paths.
            } else {
                if (normalize != NormMode.OFF) {
                    strokerat = at;
                    pi = src.getPathIterator(at);
                    pi = rdrCtx.npIterator.init(pi, normalize);
                    // by now strokerat == at && outat == null. Input paths to
                    // stroker (and maybe dasher) will have the full transform at
                    // applied to them, then they will be normalized, and then
                    // the inverse of *only the non translation part of at* will
                    // be applied to the normalized paths. This won't cause problems
                    // in stroker, because, suppose at = T*A, where T is just the
                    // translation part of at, and A is the rest. T*A has already
                    // been applied to Stroker/Dasher's input. Then Ainv will be
                    // applied. Ainv*T*A is not equal to T, but it is a translation,
                    // which means that none of stroker's assumptions about its
                    // input will be violated. After all this, A will be applied
                    // to stroker's output.
                } else {
                    outat = at;
                    pi = src.getPathIterator(null);
                    // outat == at && strokerat == null. This is because if no
                    // normalization is done, we can just apply all our
                    // transformations to stroker's output.
                }
            }
        } else {
            // either at is null or it's the identity. In either case
            // we don't transform the path.
            pi = src.getPathIterator(null);
            if (normalize != NormMode.OFF) {
                pi = rdrCtx.npIterator.init(pi, normalize);
            }
        }

        if (useSimplifier) {
            // Use simplifier after stroker before Dasher to remove collinear segments:
            pc2d = rdrCtx.simplifier.init(pc2d);
        }

        // by now, at least one of outat and strokerat will be null. Unless at is not
        // a constant multiple of an orthogonal transformation, they will both be
        // null. In other cases, outat == at if normalization is off, and if
        // normalization is on, strokerat == at.
        final TransformingPathConsumer2D transformerPC2D = rdrCtx.transformerPC2D;
        pc2d = transformerPC2D.transformConsumer(pc2d, outat);
        pc2d = transformerPC2D.deltaTransformConsumer(pc2d, strokerat);
        
        pc2d = rdrCtx.stroker.init(pc2d, width, caps, join, miterlimit);
        
        if (dashes != null) {
            if (!recycleDashes) {
                dashLen = dashes.length;
            }
            pc2d = rdrCtx.dasher.init(pc2d, dashes, dashLen, dashphase, recycleDashes);
        }
        pc2d = transformerPC2D.inverseDeltaTransformConsumer(pc2d, strokerat);
        pathTo(rdrCtx.float6, pi, pc2d);
        
        /*
         * Pipeline seems to be:
         *    shape.getPathIterator 
         * -> inverseDeltaTransformConsumer 
         * -> Dasher 
         * -> Stroker 
         * -> deltaTransformConsumer OR transformConsumer
         * 
         * -> CollinearSimplifier to remove redundant segments
         *
         * -> pc2d = Renderer (bounding box)
         */
    }

    private static boolean nearZero(double num, double nulps) {
        return Math.abs(num) < nulps * Math.ulp(num);
    }

    final static class NormalizingPathIterator implements PathIterator {

        private PathIterator src;

        // the adjustment applied to the current position.
        private float curx_adjust, cury_adjust;
        // the adjustment applied to the last moveTo position.
        private float movx_adjust, movy_adjust;

        // constants used in normalization computations
        private float lval, rval;

        private final float[] tmp;
        
        // LBO: flag to skip lval (ie != 0)
        private boolean skip_lval;

        /** per-thread renderer context */
        final RendererContext rdrCtx;

        NormalizingPathIterator(final RendererContext rdrCtx) {
            this.rdrCtx = rdrCtx;
            tmp = rdrCtx.float6;
        }
        
        NormalizingPathIterator init(PathIterator src, NormMode mode) {
            this.src = src;
            
            // TODO: use two different implementations to avoid computations with lval = 0 !
            switch (mode) {
                case ON_NO_AA:
                    // round to nearest (0.25, 0.25) pixel
                    lval = rval = 0.25f;
                    skip_lval = false;
                    break;
                case ON_WITH_AA:
                    // round to nearest pixel center
                    lval = 0f;
                    rval = 0.5f;
                    skip_lval = true; // most probable case
                    break;
                case OFF:
                    throw new InternalError("A NormalizingPathIterator should " +
                              "not be created if no normalization is being done");
                default:
                    throw new InternalError("Unrecognized normalization mode");
            }
            return this; // fluent API
        }

        @Override
        public int currentSegment(final float[] coords) {
            final int type = src.currentSegment(coords);
            
            if (doMonitors) {
                RendererContext.stats.mon_npi_currentSegment.start();
            }            

            int lastCoord;
            switch(type) {
                case PathIterator.SEG_CUBICTO:
                    lastCoord = 4;
                    break;
                case PathIterator.SEG_QUADTO:
                    lastCoord = 2;
                    break;
                case PathIterator.SEG_LINETO:
                case PathIterator.SEG_MOVETO:
                    lastCoord = 0;
                    break;
                case PathIterator.SEG_CLOSE:
                    // we don't want to deal with this case later. We just exit now
                    curx_adjust = movx_adjust;
                    cury_adjust = movy_adjust;
                    return type;
                default:
                    throw new InternalError("Unrecognized curve type");
            }

            // normalize endpoint
            
            final float x_adjust;
            final float y_adjust;
            float coord;

            // fast path (avoid lval use):
            if (skip_lval)
            {
                coord = coords[lastCoord];
                // TODO: optimize rounding coords (floor ...)
                x_adjust = (float)FastMath.floor(coord) + rval - coord;
                
                coord = coords[lastCoord + 1];
                // TODO: optimize rounding coords (floor ...)
                y_adjust = (float)FastMath.floor(coord) + rval - coord;
            } else {
                coord = coords[lastCoord];
                // TODO: optimize rounding coords (floor ...)
                x_adjust = (float)FastMath.floor(coord + lval) + rval - coord;
                
                coord = coords[lastCoord + 1];
                // TODO: optimize rounding coords (floor ...)
                y_adjust = (float)FastMath.floor(coord + lval) + rval - coord;
            }
            
            coords[lastCoord    ] += x_adjust;
            coords[lastCoord + 1] += y_adjust;

            // now that the end points are done, normalize the control points
            switch(type) {
                case PathIterator.SEG_CUBICTO:
                    coords[0] += curx_adjust;
                    coords[1] += cury_adjust;
                    coords[2] += x_adjust;
                    coords[3] += y_adjust;
                    break;
                case PathIterator.SEG_QUADTO:
                    coords[0] += 0.5f * (curx_adjust + x_adjust);
                    coords[1] += 0.5f * (cury_adjust + y_adjust);
                    break;
                case PathIterator.SEG_LINETO:
                    break;
                case PathIterator.SEG_MOVETO:
                    movx_adjust = x_adjust;
                    movy_adjust = y_adjust;
                    break;
                case PathIterator.SEG_CLOSE:
                    throw new InternalError("This should be handled earlier.");
            }
            curx_adjust = x_adjust;
            cury_adjust = y_adjust;
            
            if (doMonitors) {
                RendererContext.stats.mon_npi_currentSegment.stop();
            }            
            return type;
        }

        @Override
        public int currentSegment(final double[] coords) {
            final float[] _tmp = tmp; // dirty
            int type = this.currentSegment(_tmp);
            for (int i = 0; i < 6; i++) {
                coords[i] = _tmp[i];
            }
            return type;
        }

        @Override
        public int getWindingRule() {
            return src.getWindingRule();
        }

        @Override
        public boolean isDone() {
            if (src.isDone()) {
                this.src = null; // free source PathIterator
                return true;
            }
            return false;
        }

        @Override
        public void next() {
            src.next();
        }
    }

    static void pathTo(final float[] coords, final PathIterator pi, final PathConsumer2D pc2d) {
        /*
         * TODO: clipping bounds (lines first)
         */
        while (!pi.isDone()) {
            switch (pi.currentSegment(coords)) {
            case PathIterator.SEG_MOVETO:
                pc2d.moveTo(coords[0], coords[1]);
                break;
            case PathIterator.SEG_LINETO:
                pc2d.lineTo(coords[0], coords[1]);
                break;
            case PathIterator.SEG_QUADTO:
                pc2d.quadTo(coords[0], coords[1],
                            coords[2], coords[3]);
                break;
            case PathIterator.SEG_CUBICTO:
                pc2d.curveTo(coords[0], coords[1],
                             coords[2], coords[3],
                             coords[4], coords[5]);
                break;
            case PathIterator.SEG_CLOSE:
                pc2d.closePath();
                break;
            }
            pi.next();
        }
        
        pc2d.pathDone();
    }

    /**
     * Construct an antialiased tile generator for the given shape with
     * the given rendering attributes and store the bounds of the tile
     * iteration in the bbox parameter.
     * The {@code at} parameter specifies a transform that should affect
     * both the shape and the {@code BasicStroke} attributes.
     * The {@code clip} parameter specifies the current clip in effect
     * in device coordinates and can be used to prune the data for the
     * operation, but the renderer is not required to perform any
     * clipping.
     * If the {@code BasicStroke} parameter is null then the shape
     * should be filled as is, otherwise the attributes of the
     * {@code BasicStroke} should be used to specify a draw operation.
     * The {@code thin} parameter indicates whether or not the
     * transformed {@code BasicStroke} represents coordinates smaller
     * than the minimum resolution of the antialiasing rasterizer as
     * specified by the {@code getMinimumAAPenWidth()} method.
     * <p>
     * Upon returning, this method will fill the {@code bbox} parameter
     * with 4 values indicating the bounds of the iteration of the
     * tile generator.
     * The iteration order of the tiles will be as specified by the
     * pseudo-code:
     * <pre>
     *     for (y = bbox[1]; y < bbox[3]; y += tileheight) {
     *         for (x = bbox[0]; x < bbox[2]; x += tilewidth) {
     *         }
     *     }
     * </pre>
     * If there is no output to be rendered, this method may return
     * null.
     *
     * @param s the shape to be rendered (fill or draw)
     * @param at the transform to be applied to the shape and the
     *           stroke attributes
     * @param clip the current clip in effect in device coordinates
     * @param bs if non-null, a {@code BasicStroke} whose attributes
     *           should be applied to this operation
     * @param thin true if the transformed stroke attributes are smaller
     *             than the minimum dropout pen width
     * @param normalize true if the {@code VALUE_STROKE_NORMALIZE}
     *                  {@code RenderingHint} is in effect
     * @param bbox returns the bounds of the iteration
     * @return the {@code AATileGenerator} instance to be consulted
     *         for tile coverages, or null if there is no output to render
     * @since 1.7
     */
    @Override
    public AATileGenerator getAATileGenerator(Shape s,
                                              AffineTransform at,
                                              Region clip,
                                              BasicStroke bs,
                                              boolean thin,
                                              boolean normalize,
                                              int bbox[])
    {
        final RendererContext rdrCtx = getRendererContext();
        
        // Test if at is identity:
        final AffineTransform _at = (at != null && !at.isIdentity()) ? at : null;
        
        Renderer r;
        NormMode norm = (normalize) ? NormMode.ON_WITH_AA : NormMode.OFF;
        if (bs == null) {
            PathIterator pi;
            if (normalize) {
                pi = rdrCtx.npIterator.init(s.getPathIterator(_at), norm);
            } else {
                pi = s.getPathIterator(_at);
            }
            r = rdrCtx.renderer.init(clip.getLoX(), clip.getLoY(),
                                     clip.getWidth(), clip.getHeight(),
                                     pi.getWindingRule());
            pathTo(rdrCtx.float6, pi, r);
        } else {
            r = rdrCtx.renderer.init(clip.getLoX(), clip.getLoY(),
                                     clip.getWidth(), clip.getHeight(),
                                     PathIterator.WIND_NON_ZERO);
            strokeTo(rdrCtx, s, _at, bs, thin, norm, true, r);
        }
        if (r.endRendering()) {
            MarlinTileGenerator ptg = rdrCtx.ptg.init();
            ptg.getBbox(bbox);
            // note: do not returnRendererContext(rdrCtx) as it will be called later by renderer dispose()
            return ptg;
        }
        // dispose renderer and calls returnRendererContext(rdrCtx)
        r.dispose();
        
        // Return null to cancel AA tile generation (nothing to render)
        return null;
    }

    @Override
    public final AATileGenerator getAATileGenerator(double x, double y,
                                                    double dx1, double dy1,
                                                    double dx2, double dy2,
                                                    double lw1, double lw2,
                                                    Region clip,
                                                    int bbox[])
    {
        // REMIND: Deal with large coordinates!
        double ldx1, ldy1, ldx2, ldy2;
        boolean innerpgram = (lw1 > 0 && lw2 > 0);

        if (innerpgram) {
            ldx1 = dx1 * lw1;
            ldy1 = dy1 * lw1;
            ldx2 = dx2 * lw2;
            ldy2 = dy2 * lw2;
            x -= (ldx1 + ldx2) / 2.0;
            y -= (ldy1 + ldy2) / 2.0;
            dx1 += ldx1;
            dy1 += ldy1;
            dx2 += ldx2;
            dy2 += ldy2;
            if (lw1 > 1 && lw2 > 1) {
                // Inner parallelogram was entirely consumed by stroke...
                innerpgram = false;
            }
        } else {
            ldx1 = ldy1 = ldx2 = ldy2 = 0;
        }

        final RendererContext rdrCtx = getRendererContext();
        
        Renderer r = rdrCtx.renderer.init(clip.getLoX(), clip.getLoY(),
                                          clip.getWidth(), clip.getHeight(),
                                          Renderer.WIND_EVEN_ODD);

        r.moveTo((float) x, (float) y);
        r.lineTo((float) (x+dx1), (float) (y+dy1));
        r.lineTo((float) (x+dx1+dx2), (float) (y+dy1+dy2));
        r.lineTo((float) (x+dx2), (float) (y+dy2));
        r.closePath();

        if (innerpgram) {
            x += ldx1 + ldx2;
            y += ldy1 + ldy2;
            dx1 -= 2.0 * ldx1;
            dy1 -= 2.0 * ldy1;
            dx2 -= 2.0 * ldx2;
            dy2 -= 2.0 * ldy2;
            r.moveTo((float) x, (float) y);
            r.lineTo((float) (x+dx1), (float) (y+dy1));
            r.lineTo((float) (x+dx1+dx2), (float) (y+dy1+dy2));
            r.lineTo((float) (x+dx2), (float) (y+dy2));
            r.closePath();
        }

        r.pathDone();

        if (r.endRendering()) {
            MarlinTileGenerator ptg = rdrCtx.ptg.init();
            ptg.getBbox(bbox);
            // note: do not returnRendererContext(rdrCtx) as it will be called later by renderer dispose()
            return ptg;
        }
        // dispose renderer and calls returnRendererContext(rdrCtx)
        r.dispose();
        
        // Return null to cancel AA tile generation (nothing to render)
        return null;
    }

    /**
     * Returns the minimum pen width that the antialiasing rasterizer
     * can represent without dropouts occuring.
     * @since 1.7
     */
    @Override
    public float getMinimumAAPenSize() {
        return 0.5f;
    }

    static {
        if (PathIterator.WIND_NON_ZERO != Renderer.WIND_NON_ZERO ||
            PathIterator.WIND_EVEN_ODD != Renderer.WIND_EVEN_ODD ||
            BasicStroke.JOIN_MITER != Stroker.JOIN_MITER ||
            BasicStroke.JOIN_ROUND != Stroker.JOIN_ROUND ||
            BasicStroke.JOIN_BEVEL != Stroker.JOIN_BEVEL ||
            BasicStroke.CAP_BUTT != Stroker.CAP_BUTT ||
            BasicStroke.CAP_ROUND != Stroker.CAP_ROUND ||
            BasicStroke.CAP_SQUARE != Stroker.CAP_SQUARE)
        {
            throw new InternalError("mismatched renderer constants");
        }
    }

    /* --- RendererContext handling --- */
    /** use ThreadLocal or ConcurrentLinkedQueue to get one RendererContext */
    private static final boolean useThreadLocal;

    /* hard reference */
    final static int REF_HARD = 0;
    /* soft reference */
    final static int REF_SOFT = 1;
    /* weak reference */
    final static int REF_WEAK = 2;

    /* reference type stored in either TL or CLQ */
    static final int REF_TYPE;

    /** Per-thread TileState */
    private static final ThreadLocal<Object> rdrCtxThreadLocal;
    /** TileState queue when ThreadLocal is disabled */
    private static final ConcurrentLinkedQueue<Object> rdrCtxQueue;

    /* Static initializer to use TL or CLQ mode */
    static {
        // TL mode by default:
        useThreadLocal = isUseThreadLocal();
        rdrCtxThreadLocal = (useThreadLocal) ? new ThreadLocal<Object>() : null;
        rdrCtxQueue = (!useThreadLocal) ? new ConcurrentLinkedQueue<Object>() : null;

        // Hard reference by default:
        String refType = System.getProperty("sun.java2d.renderer.useRef", "soft");
        switch (refType) {
            default:
            case "hard":
                refType = "hard";
                REF_TYPE = REF_HARD;
                break;
            case "soft":
                refType = "soft";
                REF_TYPE = REF_SOFT;
                break;
            case "weak":
                refType = "weak";
                REF_TYPE = REF_WEAK;
                break;
        }

        /* log information at startup */
        logInfo("===============================================================================");

        final String reClass = System.getProperty("sun.java2d.renderer");

        if (MarlinRenderingEngine.class.getName().equals(reClass)) {
            logInfo("Marlin software rasterizer           = ENABLED");
            logInfo("Version                              = [" + Version.getVersion() + "]");
            logInfo("sun.java2d.renderer                  = " + reClass);
            logInfo("sun.java2d.renderer.useThreadLocal   = " + isUseThreadLocal());
            logInfo("sun.java2d.renderer.useRef           = " + refType);

            logInfo("sun.java2d.renderer.pixelsize        = " + getInitialImageSize());
            logInfo("sun.java2d.renderer.subPixel_log2_X  = " + getSubPixel_Log2_X());
            logInfo("sun.java2d.renderer.subPixel_log2_Y  = " + getSubPixel_Log2_Y());
            logInfo("sun.java2d.renderer.tileSize_log2    = " + getTileSize_Log2());
            logInfo("sun.java2d.renderer.useFastMath      = " + isUseFastMath());

            /* optimisation parameters */
            logInfo("sun.java2d.renderer.useSimplifier    = " + isUseSimplifier());

            /* debugging parameters */
            logInfo("sun.java2d.renderer.doStats          = " + isDoStats());
            logInfo("sun.java2d.renderer.doMonitors       = " + isDoMonitors());
            logInfo("sun.java2d.renderer.doChecks         = " + isDoChecks());

            /* logging parameters */
            logInfo("sun.java2d.renderer.useJul           = " + isUseJul());
            logInfo("sun.java2d.renderer.logCreateContext = " + isLogCreateContext());
            logInfo("sun.java2d.renderer.logUnsafeMalloc  = " + isLogUnsafeMalloc());

        } else {
            logInfo("sun.java2d.renderer                  = " + reClass);
        }
        logInfo("===============================================================================");
    }

    /**
     * Get the RendererContext instance dedicated to the current thread
     * @return RendererContext instance
     */
    @SuppressWarnings({"unchecked"})
    static RendererContext getRendererContext() {
        RendererContext rdrCtx = null;
        final Object ref = (useThreadLocal) ? rdrCtxThreadLocal.get() : rdrCtxQueue.poll();
        if (ref != null) {
            // resolve reference:
            rdrCtx = (REF_TYPE == REF_HARD) ? ((RendererContext) ref) : ((Reference<RendererContext>) ref).get();
        }
        /* create a new RendererContext if none is available */
        if (rdrCtx == null) {
            rdrCtx = RendererContext.createContext();
            if (useThreadLocal) {
                // update thread local reference:
                rdrCtxThreadLocal.set(rdrCtx.reference);
            }
        }
        if (doMonitors) {
            RendererContext.stats.mon_pre_getAATileGenerator.start();
        }
        return rdrCtx;
    }

    /**
     * Restore the given RendererContext instance for reuse (used by the queue mode)
     * @param rdrCtx RendererContext instance
     */
    static void returnRendererContext(final RendererContext rdrCtx) {
        if (doMonitors) {
            RendererContext.stats.mon_pre_getAATileGenerator.stop();
        }
        if (!useThreadLocal) {
            rdrCtxQueue.offer(rdrCtx.reference);
        }
    }

    /* marlin system properties */

    public static boolean isUseThreadLocal() {
        return getBoolean("sun.java2d.renderer.useThreadLocal", "false");
    }

    /**
     * Return the initial pixel size used to define initial arrays (tile AA chunk, alpha line, buckets)
     *
     * @return 64 < initial pixel size < 32768 (2048 by default)
     */
    public static int getInitialImageSize() {
        return getInteger("sun.java2d.renderer.pixelsize", 2048, 64, 32 * 1024);
    }

    /**
     * Return the log(2) corresponding to subpixel on x-axis (
     *
     * @return 1 (2 subpixels) < initial pixel size < 4 (256 subpixels) (3 by default ie 8 subpixels)
     */
    public static int getSubPixel_Log2_X() {
        return getInteger("sun.java2d.renderer.subPixel_log2_X", 3, 1, 8);
    }

    /**
     * Return the log(2) corresponding to subpixel on y-axis (
     *
     * @return 1 (2 subpixels) < initial pixel size < 8 (256 subpixels) (3 by default ie 8 subpixels)
     */
    public static int getSubPixel_Log2_Y() {
        return getInteger("sun.java2d.renderer.subPixel_log2_Y", 3, 1, 8);
    }

    /**
     * Return the log(2) corresponding to the square tile size in pixels
     *
     * @return 3 (8x8 pixels) < tile size < 8 (256x256 pixels) (5 by default ie 32x32 pixels)
     */
    public static int getTileSize_Log2() {
        return getInteger("sun.java2d.renderer.tileSize_log2", 5, 3, 8);
    }
    
    public static boolean isUseFastMath() {
        return getBoolean("sun.java2d.renderer.useFastMath", "true");
    }

    /* optimisation parameters */
    
    public static boolean isUseSimplifier() {
        return getBoolean("sun.java2d.renderer.useSimplifier", "false");
    }
    
    /* debugging parameters */
    public static boolean isDoStats() {
        return getBoolean("sun.java2d.renderer.doStats", "false");
    }

    public static boolean isDoMonitors() {
        return getBoolean("sun.java2d.renderer.doMonitors", "false");
    }

    public static boolean isDoChecks() {
        return getBoolean("sun.java2d.renderer.doChecks", "false");
    }

    /* logging parameters */
    public static boolean isUseJul() {
        return getBoolean("sun.java2d.renderer.useJul", "false");
    }

    public static boolean isLogCreateContext() {
        return getBoolean("sun.java2d.renderer.logCreateContext", "false");
    }

    public static boolean isLogUnsafeMalloc() {
        return getBoolean("sun.java2d.renderer.logUnsafeMalloc", "false");
    }
    
    /* system property utilities */

    public static boolean getBoolean(final String key, final String def) {
        return Boolean.valueOf(System.getProperty(key, def));
    }

    public static int getInteger(final String key, final int def, final int min, final int max) {
        int value = Integer.getInteger(key, def);
        /* check for invalid values */
        if (value < min || value > max) {
            logInfo("Invalid value for " + key + " = " + value + "; expect value in range[" + min + ", " + max + "] !");
            value = def;
        }
        return value;
    }
    
}
