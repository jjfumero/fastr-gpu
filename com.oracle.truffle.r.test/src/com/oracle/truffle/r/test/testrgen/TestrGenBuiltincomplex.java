/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 * 
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.testrgen;

import org.junit.*;

import com.oracle.truffle.r.test.*;

public class TestrGenBuiltincomplex extends TestBase {

    @Test
    @Ignore
    public void testcomplex1() {
        assertEval("argv <- list(0, numeric(0), numeric(0)); .Internal(complex(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    @Ignore
    public void testcomplex2() {
        assertEval("argv <- list(FALSE, FALSE, numeric(0)); .Internal(complex(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    @Ignore
    public void testcomplex3() {
        assertEval("argv <- list(0L, 1:10, c(1, 1.4142135623731, 1.73205080756888, 2, 2.23606797749979, 2.44948974278318, 2.64575131106459, 2.82842712474619, 3, 3.16227766016838)); .Internal(complex(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    @Ignore
    public void testcomplex4() {
        assertEval("argv <- list(0L, NA_real_, NA_real_); .Internal(complex(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    @Ignore
    public void testcomplex5() {
        assertEval("argv <- list(0L, c(-0.560475646552213, -0.23017748948328, 1.55870831414912, 0.070508391424576, 0.129287735160946, 1.71506498688328, 0.460916205989202, -1.26506123460653, -0.686852851893526, -0.445661970099958, 1.22408179743946, 0.359813827057364, 0.400771450594052, 0.11068271594512, -0.555841134754075, 1.78691313680308, 0.497850478229239, -1.96661715662964, 0.701355901563686, -0.472791407727934, -1.06782370598685, -0.217974914658295, -1.02600444830724, -0.72889122929114, -0.625039267849257, -1.68669331074241, 0.837787044494525, 0.153373117836515, -1.13813693701195, 1.25381492106993, 0.426464221476814, -0.295071482992271, 0.895125661045022, 0.878133487533042, 0.821581081637487, 0.688640254100091, 0.553917653537589, -0.0619117105767217, -0.305962663739917, -0.380471001012383, -0.694706978920513, -0.207917278019599, -1.26539635156826, 2.16895596533851, 1.20796199830499, -1.12310858320335, -0.402884835299076, -0.466655353623219, 0.779965118336318, -0.0833690664718293, 0.253318513994755, -0.028546755348703, -0.0428704572913161, 1.36860228401446, -0.225770985659268, 1.51647060442954, -1.54875280423022, 0.584613749636069, 0.123854243844614, 0.215941568743973, 0.379639482759882, -0.502323453109302, -0.33320738366942, -1.01857538310709, -1.07179122647558, 0.303528641404258, 0.448209778629426, 0.0530042267305041, 0.922267467879738, 2.05008468562714, -0.491031166056535, -2.30916887564081, 1.00573852446226, -0.709200762582393, -0.688008616467358, 1.0255713696967, -0.284773007051009, -1.22071771225454, 0.18130347974915, -0.138891362439045, 0.00576418589988693, 0.38528040112633, -0.370660031792409, 0.644376548518833, -0.220486561818751, 0.331781963915697, 1.09683901314935, 0.435181490833803, -0.325931585531227, 1.14880761845109, 0.993503855962119, 0.54839695950807, 0.238731735111441, -0.627906076039371, 1.36065244853001, -0.600259587147127, 2.18733299301658, 1.53261062618519, -0.235700359100477, -1.02642090030678), c(-0.710406563699301, 0.25688370915653, -0.246691878462374, -0.347542599397733, -0.951618567265016, -0.0450277248089203, -0.784904469457076, -1.66794193658814, -0.380226520287762, 0.918996609060766, -0.575346962608392, 0.607964322225033, -1.61788270828916, -0.0555619655245394, 0.519407203943462, 0.301153362166714, 0.105676194148943, -0.640706008305376, -0.849704346033582, -1.02412879060491, 0.117646597100126, -0.947474614184802, -0.490557443700668, -0.256092192198247, 1.84386200523221, -0.651949901695459, 0.235386572284857, 0.0779608495637108, -0.961856634130129, -0.0713080861235987, 1.44455085842335, 0.451504053079215, 0.0412329219929399, -0.422496832339625, -2.05324722154052, 1.13133721341418, -1.46064007092482, 0.739947510877334, 1.90910356921748, -1.4438931609718, 0.701784335374711, -0.262197489402468, -1.57214415914549, -1.51466765378175, -1.60153617357459, -0.530906522170303, -1.4617555849959, 0.687916772975828, 2.10010894052567, -1.28703047603518, 0.787738847475178, 0.76904224100091, 0.332202578950118, -1.00837660827701, -0.119452606630659, -0.280395335170247, 0.56298953322048, -0.372438756103829, 0.976973386685621, -0.374580857767014, 1.05271146557933, -1.04917700666607, -1.26015524475811, 3.2410399349424, -0.416857588160432, 0.298227591540715, 0.636569674033849, -0.483780625708744, 0.516862044313609, 0.368964527385086, -0.215380507641693, 0.0652930335253153, -0.034067253738464, 2.12845189901618, -0.741336096272828, -1.09599626707466, 0.0377883991710788, 0.310480749443137, 0.436523478910183, -0.458365332711106, -1.06332613397119, 1.26318517608949, -0.349650387953555, -0.865512862653374, -0.236279568941097, -0.197175894348552, 1.10992028971364, 0.0847372921971965, 0.754053785184521, -0.499292017172261, 0.214445309581601, -0.324685911490835, 0.0945835281735714, -0.895363357977542, -1.31080153332797, 1.99721338474797, 0.600708823672418, -1.25127136162494, -0.611165916680421, -1.18548008459731)); .Internal(complex(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    @Ignore
    public void testcomplex6() {
        assertEval("argv <- list(0L, numeric(0), numeric(0)); .Internal(complex(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    @Ignore
    public void testcomplex7() {
        assertEval("argv <- list(0L, NULL, numeric(0)); .Internal(complex(argv[[1]], argv[[2]], argv[[3]]))");
    }
}
