FILES="CollinearSimplifier Curve Renderer RendererNoAA Stroker TransformingPathConsumer2D"
# MarlinRenderingEngine
for f in $FILES
do
  echo "Processing $f"
  sed -e "s/$f/D$f/g" -e "s/\"D$f/\"$f/g" -e 's/import com.sun.javafx.geom.PathConsumer2D;//g' -e 's/PathConsumer2D/DPathConsumer2D/g' -e 's/DTransformingDPathConsumer2D/DTransformingPathConsumer2D/g' -e 's/(float) //g' -e 's/float/double/g' -e 's/Float/Double/g' -e 's/DoubleMath/FloatMath/g' -e 's/\([0-9]*\.\?[0-9]\+\)f/\1d/g' -e 's/ Curve/ DCurve/g' -e 's/Helpers/DHelpers/g' -e 's/MarlinRenderer/DMarlinRenderer/g' -e 's/RendererContext/DRendererContext/g'  -e "s/DD$f/D$f/g" -e 's/MarlinDRenderer/DMarlinRenderer/g' -e 's/doubleing/floating/g' < $f.java > D$f.java
done

echo "Processing Renderers (final)"
FILES="Renderer RendererNoAA"
for f in $FILES
do
  echo "Processing D$f"
  mv D$f.java D$f.java.orig
  sed -e "s/x1d/x1/g" -e "s/y1d/y1/g" < D$f.java.orig > D$f.java
  rm D$f.java.orig
done


# Dasher (convert type)
echo "Processing Dasher"
sed -e 's/Dasher/DDasher/g' -e 's/import com.sun.javafx.geom.PathConsumer2D;//g' -e 's/PathConsumer2D/DPathConsumer2D/g' -e 's/DTransformingDPathConsumer2D/DTransformingPathConsumer2D/g' -e 's/(float) //g' -e 's/float/double/g' -e 's/Float/Double/g' -e 's/DoubleMath/FloatMath/g' -e 's/\([0-9]*\.\?[0-9]\+\)f/\1d/g' -e 's/ Curve/ DCurve/g' -e 's/Helpers/DHelpers/g' -e 's/MarlinRenderer/DMarlinRenderer/g' -e 's/RendererContext/DRendererContext/g' -e 's/MarlinDRenderer/DMarlinRenderer/g' -e 's/copyDashArray(final double\[\] dashes)/copyDashArray(final float\[\] dashes)/g' -e 's/System.arraycopy(dashes, 0, newDashes, 0, len);/for \(int i = 0; i < len; i\+\+\) \{ newDashes\[i\] = dashes\[i\]; \}/g'< Dasher.java > DDasher.java

echo "Processing Helpers"
sed -e 's/import com.sun.javafx.geom.PathConsumer2D;//g' -e 's/PathConsumer2D/DPathConsumer2D/g' -e 's/DTransformingDPathConsumer2D/DTransformingPathConsumer2D/g' -e 's/(float) //g' -e 's/float/double/g' -e 's/Float/Double/g' -e 's/DoubleMath/FloatMath/g' -e 's/\([0-9]*\.\?[0-9]\+\)f/\1d/g' -e 's/ Curve/ DCurve/g' -e 's/Helpers/DHelpers/g' -e 's/MarlinRenderer/DMarlinRenderer/g' -e 's/RendererContext/DRendererContext/g' -e 's/MarlinDRenderer/DMarlinRenderer/g' < Helpers.java > DHelpers.java

# [( -+]+[0-9]+f matches all integer float like 0f 123f (missing .0)
# [0-9]+f matches all floats without .xx
# \.[0-9]+[ /\*\+\-\(\)] matches all missing double of float suffix

