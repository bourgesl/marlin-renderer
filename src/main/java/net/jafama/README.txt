################################################################################
Jafama 2.2, 2015/12/13

Changes since version 2.1:

- Using a more standard layout for files.
- Renamed AbstractFastMath into CmnFastMath, and factored non floating point
  methods common to FastMath and StrictFastMath here, as well as E and PI
  constants.
  Also factored out corresponding tests in a new test class.
  As a result, abs(int) and abs(long) now delegate to Math (and not StrictMath,
  which usually delegates to Math for non floating point methods) when EITHER
  FastMath or StrictFastMath delegation options are activated.
- (Strict)FastMath:
  - Look-up tables are now (by default) lazily initialized, per method type
    (i.e. calling sin(double) won't initialize look-up tables for exp(double)).
    As a result, methods of (Strict)FastMath that don't use tables can now
    safely be called from code that don't want to trigger tables initialization.
  - Added a static initTables() method, which ensures their initialization to
    avoid related slow-down later at runtime (one call from either FastMath or
    StrictFastMath inits tables for both classes).
  - Reordered "ifs" in nextAfter(float,double) and nextAfter(double,double),
    for faster worse case (as I should already have done when submitting
    JDK-8032016).
  - In powFast(double,int), reworked "ifs" to reduce the amount of code
    while preserving special cases for small powers. Is now also a tad faster.
  - Added methods:
    - From JDK-8023217:
      - multiplyExact(long,int)
      - floorDiv(long,int)
      - floorMod(long,int)
    - From JDK-5100935:
      - multiplyFull(int,int)
      - multiplyHigh(long,long)
    - That were not added previously, for Math versions being preferable due to
      eventual JVM intrinsics, but that I finally decided to add for the sake of
      completeness:
      - incrementExact(int)
      - incrementExact(long)
      - decrementExact(int)
      - decrementExact(long)
      - negateExact(int)
      - negateExact(long)
    - And finally, since we mirror each xxxExact method with a xxxBounded
      version:
      - multiplyBounded(long,int)
      - incrementBounded(int)
      - incrementBounded(long)
      - decrementBounded(int)
      - decrementBounded(long)
      - negateBounded(int)
      - negateBounded(long)
- NumbersUtils:
  - Added methods:
    - From (Strict)FastMath, so that they can be used to depart +0.0(f)
      from -0.0(f) (which is a quite low-level need) without having to depend
      on (Strict)FastMath:
      - signFromBit(float)
      - signFromBit(double)
    - Following request from P. Wendykier:
      - twoPowAsIntExact(int)
      - twoPowAsIntBounded(int)
      - twoPowAsLongExact(int)
      - twoPowAsLongBounded(int)

################################################################################
Jafama 2.1, 2014/04/30

Changes since version 2.0:

- FastMath:
  - Added hypot(double,double,double), which computes sqrt(x^2+y^2+z^2) without
    intermediate overflow or underflow.
  - Made reduction more accurate for angles of large magnitudes, by using
    reduction in [-PI/4,PI/4] and quadrant information stored in two useless
    bits of exponent, rather than reduction in [-PI,PI].
  - Added use of TWOPI_LO and TWOPI_HI in some normalization methods, to make
    them more accurate for results near quadrants limits.
  - Corrected the spec about range of reliable accuracy for sinQuick(double).
  - Little optimization for large angles reduction for tan(double).
  - For round(float) and round(double), now using algorithm by Dmitry Nadezhin
    (http://mail.openjdk.java.net/pipermail/core-libs-dev/2013-August/020247.html).
  - scalb(double) is now faster for non-huge values of scale factor
    (the same optimization, didn't seem to be worth it for float case).

- Added StrictFastMath, a strict version of FastMath (and not a fast version of
  StrictMath!), and related versions of FastMath properties:
  - jafama.strict.usejdk,
  - jafama.strict.fastlog,
  - jafama.strict.fastsqrt.
  Note that now, to avoid look-up tables and their initialization overhead,
  you must set both jafama.usejdk (which only applies to FastMath) and
  jafama.strict.usejdk to true.

- NumbersUtils:
  - Added DOUBLE_MIN_NORMAL and FLOAT_MIN_NORMAL constants (Double.MIN_NORMAL
    and Float.MIN_NORMAL being available only from Java 6).
  - Added methods:
    - absNeg(int) (returns -abs(value), exact even for Integer.MIN_VALUE)
    - absNeg(long) (returns -abs(value), exact even for Long.MIN_VALUE)
    - pow2_strict(float)
    - pow2_strict(double)
    - pow3_strict(float)
    - pow3_strict(double)
    - plus2PI(double)
    - plus2PI_strict(double)
    - minus2PI(double)
    - minus2PI_strict(double)
    - plusPI(double)
    - plusPI_strict(double)
    - minusPI(double)
    - minusPI_strict(double)
    - plusPIO2(double)
    - plusPIO2_strict(double)
    - minusPIO2(double)
    - minusPIO2_strict(double)
    - toStringCSN(double) (to get "1.5E1" or "1.0E-1", instead of "15.0" or
      "0.1")
    - toStringNoCSN(double) (to get "123456789.0" or "0.0001", instead of
      "1.23456789E8" or "1.0E-4")
  - Optimized floorPowerOfTwo(int) and ceilingPowerOfTwo(int).

################################################################################
Jafama 2.0, 2013/02/20

Changes since version 1.2:
- Changed license from GNU LGPL V3 to Apache V2.
- Is now Java 5 compatible (but some unit tests require Java 6).
- Renamed package from odk.lang into net.jafama.
- Renamed properties from odk.fastmath.xxx into jafama.xxx.
- To match upcoming JDK8's new Math methods, renamed:
  - toIntSafe into toIntExact (and asInt in NumbersUtils)
  - plusNoModulo into addBounded (and plusBounded in NumbersUtils)
  - plusNoModuloSafe into addExact (and plusExact in NumbersUtils)
  - minusNoModulo into subtractBounded (and minusBounded in NumbersUtils)
  - minusNoModuloSafe into subtractExact (and minusExact in NumbersUtils)
  - timesNoModulo into multiplyBounded (and timesBounded in NumbersUtils)
  - timesNoModuloSafe into multiplyExact (and timesExact in NumbersUtils)
- Corrected Javadoc (accuracy claims) for some xxxQuick methods.
- Removed usage of strictfp, which can cause an overhead.
  As a result, behavior might change depending on architecture,
  as well as dynamically due to JIT (ex.: pow2(double)).
- Minor typos, refactorings and doc enhancements.
- NumbersUtils:
  - Renamed mask methods, such as leftBit0LongMask into longMaskMSBits0.
  - Added methods:
    - isMathematicalInteger(float)
    - isMathematicalInteger(double)
    - isEquidistant(float)
    - isEquidistant(double)
    - isNaNOrInfinite(float)
  - Upgraded some implementations.
- FastMath:
  - In spec., added warning about possible FastMath slowness and
    initialization overhead.
  - FastMath now only depends on Java 5, and therefore, when delegating to
    Math, only does so for Math methods that exist in Java 5 and have the
    same semantics (except for accuracy).
  - Modified some treatments for JVM crash after JIT-optimization to
    (hopefully) not occur (crashes observed with Java 6u29, with which
    workarounds were tested).
  - Removed usage of StrictMath, and related property, which is allowed due
    to spec. relaxation by strictfp removal, to make things both simpler,
    and possibly faster.
    As a result, for FastMath.log(double), we now use Math.log(double) by
    default instead of our redefined method, which was used before due to
    StrictMath.log(double) being possibly very slow. This makes log10(double),
    log1p(double), logQuick(double), pow(double,double) and
    powQuick(double,double) more accurate.
  - Changed sinAndCos(double,DoubleWrapper,DoubleWrapper) into
    sinAndCos(double,DoubleWrapper), and
    sinhAndCosh(double,DoubleWrapper,DoubleWrapper) into
    sinhAndCosh(double,DoubleWrapper).
  - exp(double) is now more accurate (removed special handling for subnormals,
    which is useless with proper multiplications order).
    This makes hyperbolic trigonometry functions, expm1(double),
    pow(double,double) and powQuick(double,double) more accurate.
  - round(float) and round(double) now no longer follow Math class, which
    spec. and behavior changed over time, but just round-up properly.
  - For asinInRange and acosInRange, replaced < and > with <= and >=,
    for quick return in case input is a bound.
  - Removed internal usage of look-up table to compute double powers of two,
    for it doesn't speed things up much, and to avoid possible cache-misses
    for methods that are now table-free.
  - Added methods:
    - coshm1(double)
    - asinh(double)
    - acosh(double)
    - acosh1p(double)
    - atanh(double)
    - log10(double)
    - sqrtQuick(double)
    - invSqrtQuick(double)
    - roundEven(float)
    - roundEven(double)
    - rint(float)
    - rint(double)
    - abs(long)
    - floorDiv(int,int)
    - floorDiv(long,long)
    - floorMod(int,int)
    - floorMod(long,long)
    - isNaNOrInfinite(float)
    - signum(float)
    - signum(double)
    - signFromBit(float)
    - signFromBit(double)
    - copySign(float,float)
    - copySign(double,double)
    - ulp(float)
    - ulp(double)
    - nextAfter(float,double)
    - nextAfter(double,double)
    - nextDown(float)
    - nextDown(double)
    - nextUp(float)
    - nextUp(double)
    - scalb(float,int)
    - scalb(double,int)
  - Separated accuracy/correctness tests from benches, and
    improved/simplified both.

################################################################################
Jafama 1.2, 2011/03/19

Changes since version 1.1:
- Now using StrictMath to compute constants and look-up tables, to ensure
  consistency across various architectures.
- Now using Math.abs(double) directly instead of FastMath.abs(double), since
  this method is not redefined.
- Added PI_SUP constant, the closest upper approximation of Pi as double,
  especially useful to define a span that covers full angular range
  (2*Math.PI doesn't).
- Added log2(long), log2(int).
- Added odk.fastmath.strict, odk.fastmath.usejdk, odk.fastmath.fastlog and
  odk.fastmath.fastsqrt properties. See FastMath Javadoc for details.
  NB: As a consequence, by default, a redefined log(double) is now used instead
      of Math.log(double), for non-redefined treatments now use StrictMath by
      default, and StrictMath.log(double) seems usually slow.
- Simplified toString() implementation for IntWrapper and DoubleWrapper classes.
- Completed Javadoc and updated tests for FastMath.remainder(double,double)
  method, which does not behave as Math.IEEEremainder(double,double).
- Moved some basic numbers related treatments, into a new class (NumbersUtils),
  since they are very low-level and can be used in many places where a
  dependency to the heavy (look-up tables) FastMath class could be considered
  inappropriate.
  These treatments are still available from FastMath class.
- In benches, made sure dummy variables are used, to avoid treatments to be
  optimized away (has not been observed, but might have been with some JVMs).

################################################################################
Jafama 1.1, 2009/12/05

Changes since version 1.0:
- for asin pow tabs, use of powFast(double,int) instead of pow(double,double),
- added expQuick(double), logQuick(double), powQuick(double),
- changed random numbers computation for tests.

################################################################################
Jafama 1.0, 2009/07/25

- Placed under the GNU Lesser General Public License, version 3.

- Requires Java 1.6 or later.

- src folder contains the code.

- test folder contains some tests (some of which require JUnit).

- The odk.lang package is due to this code being a core part of ODK
  library (Optimized Development Kit, of which only this code is
  open source).

- Copy/paste of FastMath class comments:

 * Class providing math treatments that:
 * - are meant to be faster than those of java.lang.Math class (depending on
 *   JVM or JVM options, they might be slower),
 * - are still somehow accurate and robust (handling of NaN and such),
 * - do not (or not directly) generate objects at run time (no "new").
 * 
 * Other than optimized treatments, a valuable feature of this class is the
 * presence of angles normalization methods, derived from those used in
 * java.lang.Math (for which, sadly, no API is provided, letting everyone
 * with the terrible responsibility to write their own ones).
 * 
 * Non-redefined methods of java.lang.Math class are also available,
 * for easy replacement.
 
################################################################################
