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

public class TestrGenBuiltiniscall extends TestBase {

    @Test
    @Ignore
    public void testiscall1() {
        assertEval("argv <- list(structure(list(dim = c(93L, 19L), dimnames = list(c(\'1\', \'2\', \'3\', \'4\', \'5\', \'6\', \'7\', \'8\', \'9\', \'10\', \'11\', \'12\', \'13\', \'14\', \'15\', \'16\', \'17\', \'18\', \'19\', \'20\', \'21\', \'22\', \'23\', \'24\', \'25\', \'26\', \'27\', \'28\', \'29\', \'30\', \'31\', \'32\', \'33\', \'34\', \'35\', \'36\', \'37\', \'38\', \'39\', \'40\', \'41\', \'42\', \'43\', \'44\', \'45\', \'46\', \'47\', \'48\', \'49\', \'50\', \'51\', \'52\', \'53\', \'54\', \'55\', \'56\', \'57\', \'58\', \'59\', \'60\', \'61\', \'62\', \'63\', \'64\', \'65\', \'66\', \'67\', \'68\', \'69\', \'70\', \'71\', \'72\', \'73\', \'74\', \'75\', \'76\', \'77\', \'78\', \'79\', \'80\', \'81\', \'82\', \'83\', \'84\', \'85\', \'86\', \'87\', \'88\', \'89\', \'90\', \'91\', \'92\', \'93\'), c(\'dfb.1_\', \'dfb.Wght\', \'dfb.Cyl4\', \'dfb.Cyl5\', \'dfb.Cyl6\', \'dfb.Cyl8\', \'dfb.Cyln\', \'dfb.TypL\', \'dfb.TypM\', \'dfb.TypSm\', \'dfb.TypSp\', \'dfb.TypV\', \'dfb.EngS\', \'dfb.DrTF\', \'dfb.DrTR\', \'dffit\', \'cov.r\', \'cook.d\', \'hat\'))), .Names = c(\'dim\', \'dimnames\')));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall2() {
        assertEval("argv <- list(structure(1:10, .Tsp = c(1920.5, 1921.25, 12), class = \'ts\'));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall3() {
        assertEval("argv <- list(structure(c(1960, 1960, 1960, 1960, 1960, 1960, 1960, 1960, 1960, 1960, 1960, 1961, 1961, 1961, 1961, 1961, 1961, 1961, 1961, 1961), .Tsp = c(1960.08333333333, 1961.66666666667, 12), class = \'ts\'));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall4() {
        assertEval("argv <- list(structure(3.14159265358979, class = structure(\'3.14159265358979\', class = \'testit\')));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall5() {
        assertEval("argv <- list(structure(list(), .Names = character(0), row.names = integer(0), class = \'data.frame\'));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall6() {
        assertEval("argv <- list(structure(list(var = structure(c(4L, 4L, 1L, 2L, 1L, 2L, 3L, 1L, 2L, 1L, 2L, 1L, 1L, 1L, 2L, 3L, 2L, 1L, 3L, 1L, 1L, 1L, 3L, 1L, 1L), .Label = c(\'<leaf>\', \'Age\', \'Number\', \'Start\'), class = \'factor\'), wt = c(318, 244, 114, 130, 49, 81, 65, 34, 31, 17, 14, 3, 11, 16, 74, 42, 35, 16, 19, 10, 9, 7, 32, 3, 29), dev = c(95.4, 34.5652173913044, 0, 34.5652173913044, 0, 34.5652173913044, 15.2086956521739, 0, 15.2086956521739, 0, 5.36385542168675, 0, 0, 3.57590361445783, 53.6385542168675, 22.1217391304348, 12.4434782608696, 0, 12.4434782608696, 0, 0, 0, 7.15180722891566, 0, 1.78795180722892), yval = c(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 2, 2, 2, 1, 1, 1, 1, 1, 2, 2, 2, 1, 2), complexity = c(0.165415284325709, 0.0551384281085695, 0.039, 0.0551384281085695, 0.039, 0.0551384281085695, 0.0515976951283394, 0.039, 0.0515976951283394, 0.039, 0.0515976951283394, 0.039, 0.039, 0.039, 0.165415284325709, 0.101449275362319, 0.0652173913043478, 0.039, 0.0652173913043478, 0.039, 0.039, 0.039, 0.0562248995983936, 0.039, 0.039), ncompete = c(2L, 2L, 0L, 2L, 0L, 2L, 2L, 0L, 2L, 0L, 2L, 0L, 0L, 0L, 2L, 2L, 2L, 0L, 2L, 0L, 0L, 0L, 2L, 0L, 0L), nsurrogate = c(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L), yval2 = structure(c(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 2, 2, 2, 1, 1, 1, 1, 1, 2, 2, 2, 1, 2, 249, 219, 114, 105, 49, 56, 54, 34, 20, 17, 3, 3, 0, 2, 30, 26, 26, 16, 10, 10, 0, 0, 4, 3, 1, 69, 25, 0, 25, 0, 25, 11, 0, 11, 0, 11, 0, 11, 14, 44, 16, 9, 0, 9, 0, 9, 7, 28, 0, 28, 0.7, 0.849942146369685, 1, 0.730869001297017, 1, 0.591562233449249, 0.760430333245867, 1, 0.540359120657828, 1, 0.149906890130354, 1, 0, 0.0845588235294117, 0.305967312808818, 0.512362301101591, 0.651314765831648, 1, 0.418073227733056, 1, 0, 0, 0.0845588235294117, 1, 0.0225711481844946, 0.3, 0.150057853630315, 0, 0.269130998702983, 0, 0.408437766550751, 0.239569666754133, 0, 0.459640879342172, 0, 0.850093109869646, 0, 1, 0.915441176470588, 0.694032687191182, 0.487637698898409, 0.348685234168352, 0, 0.581926772266944, 0, 1, 1, 0.915441176470588, 0, 0.977428851815505, 1, 0.724358302776323, 0.320481927710843, 0.403876375065479, 0.137751004016064, 0.266125371049415, 0.199633315872184, 0.095582329317269, 0.104050986554915, 0.0477911646586345, 0.0562598218962808, 0.00843373493975903, 0.0478260869565217, 0.0664920551772307, 0.275641697223677, 0.142657586869216, 0.11222280426052, 0.0449799196787148, 0.0672428845818055, 0.0281124497991968, 0.0391304347826087, 0.0304347826086957, 0.132984110354461, 0.00843373493975903, 0.124550375414702), .Dim = c(25L, 6L), .Dimnames = list(NULL, c(\'\', \'\', \'\', \'\', \'\', \'nodeprob\')))), .Names = c(\'var\', \'wt\', \'dev\', \'yval\', \'complexity\', \'ncompete\', \'nsurrogate\', \'yval2\'), class = \'data.frame\', row.names = c(1L, 2L, 4L, 5L, 10L, 11L, 22L, 44L, 45L, 90L, 91L, 182L, 183L, 23L, 3L, 6L, 12L, 24L, 25L, 50L, 51L, 13L, 7L, 14L, 15L)));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall7() {
        assertEval("argv <- list(structure(c(2.828, -1.04, -2.738, 3.084), .Names = c(\'(Intercept)\', \'age\', \'wgt\', \'prot\')));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall8() {
        assertEval("argv <- list(structure(list(a = 1), .Dim = 1L, .Dimnames = list(\'a\')));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall9() {
        assertEval("argv <- list(c(0, 0, 0, 0, 0, 1.75368801162502e-134, 0, 0, 0, 2.60477585273833e-251, 1.16485035372295e-260, 0, 1.53160350210786e-322, 0.333331382328728, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3.44161262707711e-123, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.968811545398e-173, 0, 8.2359965384697e-150, 0, 0, 0, 0, 6.51733217171341e-10, 0, 2.36840184577368e-67, 0, 9.4348408357524e-307, 0, 1.59959906013771e-89, 0, 8.73836857865034e-286, 7.09716190970992e-54, 0, 0, 0, 1.530425353017e-274, 8.57590058044551e-14, 0.333333106397154, 0, 0, 1.36895217898448e-199, 2.0226102635783e-177, 5.50445388209462e-42, 0, 0, 0, 0, 1.07846402051283e-44, 1.88605464411243e-186, 1.09156111051203e-26, 0, 3.0702877273237e-124, 0.333333209689785, 0, 0, 0, 0, 0, 0, 3.09816093866831e-94, 0, 0, 4.7522727332095e-272, 0, 0, 2.30093251441394e-06, 0, 0, 1.27082826644707e-274, 0, 0, 0, 0, 0, 0, 0, 4.5662025456054e-65, 0, 2.77995853978268e-149, 0, 0, 0));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall10() {
        assertEval("argv <- list(structure(c(1+2i, 5+0i, 3-4i, -6+0i), .Dim = c(2L, 2L)));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall11() {
        assertEval("argv <- list(c(TRUE, FALSE, TRUE, NA, FALSE, FALSE, TRUE));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall12() {
        assertEval("argv <- list(structure(c(NA, 9.93, 26.79, 820.91), .Names = c(\'<none>\', \'- x4\', \'- x2\', \'- x1\')));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall13() {
        assertEval("argv <- list(structure(list(nationality = structure(c(1L, 2L, 2L, 3L, 3L, 1L), .Label = c(\'Australia\', \'UK\', \'US\'), class = \'factor\'), deceased = structure(c(1L, 1L, 1L, 1L, 2L, 1L), .Label = c(\'no\', \'yes\'), class = \'factor\'), title = structure(c(3L, 6L, 7L, 4L, 2L, 5L), .Label = c(\'An Introduction to R\', \'Exploratory Data Analysis\', \'Interactive Data Analysis\', \'LISP-STAT\', \'Modern Applied Statistics ...\', \'Spatial Statistics\', \'Stochastic Simulation\'), class = \'factor\'), other.author = structure(c(NA, NA, NA, NA, NA, 1L), .Label = c(\'Ripley\', \'Venables & Smith\'), class = \'factor\')), .Names = c(\'nationality\', \'deceased\', \'title\', \'other.author\'), class = \'data.frame\', row.names = c(NA, -6L)));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall14() {
        assertEval("argv <- list(structure(c(10L, 10L, 10L, 10L, 10L), .Dim = 5L, .Dimnames = structure(list(a = c(\'0.333333333333333\', \'0.5\', \'1\', \'Inf\', NA)), .Names = \'a\')));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall15() {
        assertEval("argv <- list(1.79769313486232e+308);is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall16() {
        assertEval("argv <- list(c(3.71357206670431, 3.58351893845611, 2.484906649788, 2.89037175789616, NA, 3.3322045101752, 3.13549421592915, 2.94443897916644, 2.07944154167984, NA, 1.94591014905531, 2.77258872223978, 2.39789527279837, 2.63905732961526, 2.89037175789616, 2.63905732961526, 3.52636052461616, 1.79175946922805, 3.40119738166216, 2.39789527279837, 0, 2.39789527279837, 1.38629436111989, 3.46573590279973, NA, NA, NA, 3.13549421592915, 3.80666248977032, 4.74493212836325, 3.61091791264422, NA, NA, NA, NA, NA, NA, 3.36729582998647, NA, 4.26267987704132, 3.66356164612965, NA, NA, 3.13549421592915, NA, NA, 3.04452243772342, 3.61091791264422, 2.99573227355399, 2.484906649788, 2.56494935746154, NA, NA, NA, NA, NA, NA, NA, NA, NA, NA, 4.90527477843843, 3.89182029811063, 3.46573590279973, NA, 4.15888308335967, 3.68887945411394, 4.34380542185368, 4.57471097850338, 4.57471097850338, 4.44265125649032, NA, 2.30258509299405, 3.29583686600433, NA, 1.94591014905531, 3.87120101090789, 3.55534806148941, 4.11087386417331, 4.36944785246702, 4.14313472639153, 2.77258872223978, NA, NA, 4.38202663467388, 4.68213122712422, 2.99573227355399, 3.95124371858143, 4.40671924726425, 3.91202300542815, 4.15888308335967, 4.07753744390572, 3.66356164612965, 2.19722457733622, 2.77258872223978, 4.35670882668959, 3.55534806148941, 4.18965474202643, 4.80402104473326, 4.48863636973214, 4.70048036579242, NA, NA, 3.78418963391826, 3.3322045101752, 4.17438726989564, NA, 3.09104245335832, 4.07753744390572, 3.13549421592915, 3.43398720448515, 3.78418963391826, 3.04452243772342, 2.19722457733622, NA, 3.80666248977032, 5.12396397940326, 4.29045944114839, NA, 4.33073334028633, 4.77068462446567, 4.43081679884331, 4.44265125649032, 4.56434819146784, 4.35670882668959, 4.29045944114839, 4.51085950651685, 3.85014760171006, 3.46573590279973, 2.99573227355399, 3.13549421592915, 3.04452243772342, 3.17805383034795, 3.78418963391826, 3.04452243772342, 3.3322045101752, 2.19722457733622, 2.56494935746154, 3.8286413964891, 2.89037175789616, 2.56494935746154, 3.17805383034795, 2.77258872223978, 2.56494935746154, 3.13549421592915, 3.58351893845611, 1.94591014905531, 2.63905732961526, 3.40119738166216, NA, 2.63905732961526, 2.89037175789616, 2.99573227355399));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall17() {
        assertEval("argv <- list(c(1.1+0i, NA, 3+0i));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall18() {
        assertEval("argv <- list(structure(c(\'***\', \'***\', \'*\', \'*\'), legend = \'0 ‘***’ 0.001 ‘**’ 0.01 ‘*’ 0.05 ‘.’ 0.1 ‘ ’ 1\', class = \'noquote\'));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall19() {
        assertEval("argv <- list(structure(3.14159265358979, .Tsp = c(1, 1, 1), class = \'ts\'));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall20() {
        assertEval("argv <- list(structure(c(0.00290239468554394, 0.00140705152597278, 0.00182415100508828, 0.000171517300342801, 0.0747454613066304, 0.00103234723292907, 0.000179983318697126, 0.0352586084465556, 0.00336847595628202, 0.0640696486471412, 0.013210800275195, 0.00194778778741287, 0.00351950115137133, 0.000700468320296444, 0.00252844357735001, 0.0143720121954949, 0.0092342255427433, 7.64817786749446e-06, 0.00387339857745543, 0.00121246491006704, 0.00624917129689855, 0.00187753034805145, 0.000103002251547083, 0.0136703020254034, 0.000349542811339773, 0.00120367047056317, 0.00194205014408538, 0.00462815827742801, 0.000149291834133954, 0.00193441236645676, 9.00084520363835e-05, 0.0160915134527436, 0.0034667595853861, 0.00481936427422654, 3.13343033856193e-05, 0.0564685345533007, 0.00929771993193245, 0.0103876340982416, 0.0133005891226511, 0.0325989357511191, 0.00228122925969391, 0.0460976655088242, 0.0030036374596782, 0.000271060875811076, 0.0301696315261026, 4.72002631048293e-05, 0.0262321004865234, 0.00594174673473018, 0.00288915040856097, 0.00635277836091401, 0.00569342819072192, 0.0163907345734164, 0.000360581939026221, 0.000237725871915377, 0.0164062036225434, 0.0238391417439455, NaN, 0.0421542087325977, 0.00133954856768466, 0.0113421570571087, 0.00818242287729128, 0.000149291834133954, 0.00162069399881579, 0.00180262291288582, 0.0043164627226381, 0.000407784303899558, 0.0087630128035445, 0.00179253664026378, 0.000416739394150714, 0.0143720121954949, 0.000179983318697137, 0.00115986529332947, 0.00377736311314377, 0.00219491136307178, 0.000700468320296447, 0.000522557531637987, 9.86336244510677e-05, 0.0216346027446621, 0.000659639144027213, 0.0137501462695059, 5.91425796333253e-08, 0.0279425064631674, 0.000170828237014783, 0.00424546903556132, 0.0114879015536739, 0.000173346990819205, 0.00138111062254461, 0.00772582941114727, 0.0277947034678616, 0.00892024547056825, 0.061857770987456, 0.0125790610228498, 0.0277947034678616), .Names = c(\'1\', \'2\', \'3\', \'4\', \'5\', \'6\', \'7\', \'8\', \'9\', \'10\', \'11\', \'12\', \'13\', \'14\', \'15\', \'16\', \'17\', \'18\', \'19\', \'20\', \'21\', \'22\', \'23\', \'24\', \'25\', \'26\', \'27\', \'28\', \'29\', \'30\', \'31\', \'32\', \'33\', \'34\', \'35\', \'36\', \'37\', \'38\', \'39\', \'40\', \'41\', \'42\', \'43\', \'44\', \'45\', \'46\', \'47\', \'48\', \'49\', \'50\', \'51\', \'52\', \'53\', \'54\', \'55\', \'56\', \'57\', \'58\', \'59\', \'60\', \'61\', \'62\', \'63\', \'64\', \'65\', \'66\', \'67\', \'68\', \'69\', \'70\', \'71\', \'72\', \'73\', \'74\', \'75\', \'76\', \'77\', \'78\', \'79\', \'80\', \'81\', \'82\', \'83\', \'84\', \'85\', \'86\', \'87\', \'88\', \'89\', \'90\', \'91\', \'92\', \'93\')));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall21() {
        assertEval("argv <- list(structure(c(1920, 1920, 1920, 1920, 1920, 1920, 1921, 1921, 1921, 1921), .Tsp = c(1920.5, 1921.25, 12), class = \'ts\'));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall23() {
        assertEval("argv <- list(structure(c(2671, 6.026e+77, 3.161e+152, 3.501e+299, 2.409e+227, 1.529e+302), .Names = c(\'Min.\', \'1st Qu.\', \'Median\', \'Mean\', \'3rd Qu.\', \'Max.\')));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall24() {
        assertEval("argv <- list(structure(c(1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 7, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 10, 11, 11, 11, 12, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15, 16, 16, 16, 17, 17, 17, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 21, 21, 21, 21, 21, 21, 21, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 7, 7.4, 7.4, 7.4, 7.4, 7.4, 7.4, 7.4, 7.4, 7.4, 7.4, 5.3, 6.1, 6.1, 6.1, 6.1, 6.1, 6.1, 6.1, 6.1, 6.1, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 5.6, 5.7, 5.3, 5.3, 5.3, 5.3, 5.3, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 6.6, 5.3, 7.7, 7.7, 7.7, 6.2, 5.6, 5.6, 5.2, 5.2, 5.2, 5.2, 6, 6, 6, 6, 5.1, 5.1, 5.1, 7.6, 7.6, 7.6, 5.8, 5.8, 5.8, 5.8, 5.8, 5.8, 5.8, 5.8, 5.8, 5.8, 5.8, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 6.5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5.8, 5.8, 5.8, 5.8, 5.8, 5.8, 5.8, 5.5, 5.5, 5.5, 5.5, 5.5, 5.5, 5.5, 5.5, 5.5, 5.5, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 5.3, 24, 13, 15, 68, 39, 74, 22, 1, 8, 55, 24, 20, 51, 13, 3, 4, 5, 6, 15, 2, 8, 60, 67, 23, 58, 24, 22, 21, 37, 74, 59, 39, 14, 14, 19, 23, 70, 21, 22, 31, 29, 30, 45, 58, 17, 7, 19, 26, 39, 74, 57, 59, 12, 72, 70, 37, 64, 16, 18, 21, 22, 8, 62, 61, 63, 71, 105, 64, 10, 41, 8, 27, 11, 34, 32, 33, 68, 107, NA, 66, NA, 65, 48, 52, 43, 47, 46, 44, 41, 54, 28, 50, 40, NA, 69, NA, 75, 109, NA, 86, 112, 110, 104, 24, 111, 87, NA, NA, 92, 73, 85, 90, 89, NA, 83, NA, 102, NA, 108, 88, 91, 93, NA, 94, 84, NA, 106, NA, 95, 82, 56, 87, 109, 75, 104, 110, 112, 111, 24, 73, 85, 86, 90, 89, 102, 88, 92, 9, 49, 42, 38, 35, 36, 25, NA, NA, 9, 49, 42, NA, 36, 38, 25, 53, 79, 78, 103, 77, 80, 114, 97, 113, 76, 96, 81, 116, 99, 117, 115, 98, 101, 100, 12, 148, 42, 85, 107, 109, 156, 224, 293, 359, 370, 8, 16.1, 63.6, 6.6, 9.3, 13, 17.3, 105, 112, 123, 105, 122, 141, 200, 45, 130, 147, 187, 197, 203, 211, 62, 62, 19, 21, 13, 22, 29, 17, 19.6, 20.2, 21.1, 21.9, 24.2, 66, 87, 23.4, 24.6, 25.7, 28.6, 37.4, 46.7, 56.9, 60.7, 61.4, 62, 64, 82, 88, 91, 31, 45, 145, 300, 5, 50, 16, 17, 8, 10, 10, 8, 32, 30, 31, 2.9, 3.2, 7.6, 25.4, 32.9, 92.2, 1.2, 1.6, 9.1, 3.7, 5.3, 7.4, 17.9, 19.2, 23.4, 30, 38.9, 23.5, 26, 0.5, 0.6, 1.3, 1.4, 2.6, 3.8, 4, 5.1, 6.2, 6.8, 7.5, 7.6, 8.4, 8.5, 8.5, 10.6, 12.6, 12.7, 12.9, 14, 15, 16, 17.7, 18, 22, 22, 23, 23.2, 29, 32, 32.7, 36, 43.5, 49, 60, 64, 7.5, 8.8, 8.9, 9.4, 9.7, 9.7, 10.5, 10.5, 12, 12.2, 12.8, 14.6, 14.9, 17.6, 23.9, 25, 10.8, 15.7, 16.7, 20.8, 28.5, 33.1, 40.3, 4, 10.1, 11.1, 17.7, 22.5, 26.5, 29, 30.9, 37.8, 48.3, 5.8, 12, 12.1, 20.5, 20.5, 25.3, 35.9, 36.1, 36.3, 38.5, 41.4, 43.6, 44.4, 46.1, 47.1, 47.7, 49.2, 53.1, 0.359, 0.014, 0.196, 0.135, 0.062, 0.054, 0.014, 0.018, 0.01, 0.004, 0.004, 0.127, 0.411, 0.018, 0.509, 0.467, 0.279, 0.072, 0.012, 0.006, 0.003, 0.018, 0.048, 0.011, 0.007, 0.142, 0.031, 0.006, 0.01, 0.01, 0.006, 0.013, 0.005, 0.003, 0.086, 0.179, 0.205, 0.073, 0.045, 0.374, 0.2, 0.147, 0.188, 0.204, 0.335, 0.057, 0.021, 0.152, 0.217, 0.114, 0.15, 0.148, 0.112, 0.043, 0.057, 0.03, 0.027, 0.028, 0.034, 0.03, 0.039, 0.03, 0.11, 0.01, 0.01, 0.39, 0.031, 0.13, 0.011, 0.12, 0.17, 0.14, 0.11, 0.04, 0.07, 0.08, 0.21, 0.39, 0.28, 0.16, 0.064, 0.09, 0.42, 0.23, 0.13, 0.26, 0.27, 0.26, 0.11, 0.12, 0.038, 0.044, 0.046, 0.17, 0.21, 0.32, 0.52, 0.72, 0.32, 0.81, 0.64, 0.56, 0.51, 0.4, 0.61, 0.26, 0.24, 0.46, 0.22, 0.23, 0.28, 0.38, 0.27, 0.31, 0.2, 0.11, 0.43, 0.27, 0.15, 0.15, 0.15, 0.13, 0.19, 0.13, 0.066, 0.35, 0.1, 0.16, 0.14, 0.049, 0.034, 0.264, 0.263, 0.23, 0.147, 0.286, 0.157, 0.237, 0.133, 0.055, 0.097, 0.129, 0.192, 0.147, 0.154, 0.06, 0.057, 0.12, 0.154, 0.052, 0.045, 0.086, 0.056, 0.065, 0.259, 0.267, 0.071, 0.275, 0.058, 0.026, 0.039, 0.112, 0.065, 0.026, 0.123, 0.133, 0.073, 0.097, 0.096, 0.23, 0.082, 0.11, 0.11, 0.094, 0.04, 0.05, 0.022, 0.07, 0.08, 0.033, 0.017, 0.022), .Dim = c(182L, 5L), .Dimnames = list(NULL, c(\'event\', \'mag\', \'station\', \'dist\', \'accel\'))));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall25() {
        assertEval("argv <- list(structure(c(8.444, 12.244, 11.967, 32.826), .Names = c(\'+ Temp\', \'<none>\', \'+ Soft\', \'- M.user\')));is.call(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testiscall26() {
        assertEval("argv <- list(quote(cbind(X, M) ~ M.user + Temp + M.user:Temp));is.call(argv[[1]]);");
    }
}
