package com.oracle.truffle.r.test.testrgen;

import java.util.*;
import org.junit.*;
import com.oracle.truffle.r.test.*;
public class  TestrGenBuiltincolSums extends TestBase {
	@Test
		public void testcolSums1(){
		assertEval("argv <- list(structure(c(365, 365, 365, 366, 1, 0), .Dim = c(3L, 2L)), 3, 2, FALSE); .Internal(colSums(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
	}
	@Test
		public void testcolSums2(){
		assertEval("argv <- list(structure(c(1L, 0L, 0L, 0L, 2L, 0L, 0L, 0L, 3L), .Dim = c(3L, 3L)), 3, 3, FALSE); .Internal(colSums(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
	}
	@Test
		public void testcolSums3(){
		assertEval("argv <- list(structure(c(5, 29, 14, 16, 15, 54, 14, 10, 20, 84, 17, 94, 68, 119, 26, 7), .Dim = c(4L, 4L), .Dimnames = structure(list(Hair = c(\'Black\', \'Brown\', \'Red\', \'Blond\'), Eye = c(\'Green\', \'Hazel\', \'Blue\', \'Brown\')), .Names = c(\'Hair\', \'Eye\'))), 4, 4, FALSE); .Internal(colSums(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
	}
	@Test
		public void testcolSums4(){
		assertEval("argv <- list(structure(c(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, NA, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 49, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 21, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, NA, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 21, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 9, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), .Dim = c(16L, 16L), .Dimnames = list(NULL, NULL)), 16, 16, TRUE); .Internal(colSums(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
	}
	@Test
		public void testcolSums5(){
		assertEval("argv <- list(structure(c(FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, NA, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, NA, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE), .Dim = c(16L, 16L), .Dimnames = list(NULL, NULL)), 16, 16, FALSE); .Internal(colSums(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
	}
	@Test
		public void testcolSums6(){
		assertEval("argv <- list(structure(numeric(0), .Dim = c(0L, 0L)), 0, 0, FALSE); .Internal(colSums(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
	}
	@Test
		public void testcolSums7(){
		assertEval("argv <- list(structure(c(-7.5, -6.5, -5.5, -4.5, -3.5, -2.5, -1.5, -0.5, 0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, -421.875, -274.625, -166.375, -91.125, -42.875, -15.625, -3.375, -0.125, 0.125, 3.375, 15.625, 42.875, 91.125, 166.375, 274.625, 421.875, -9187.5, -2866.5, -445.499999999999, -4.5, -283.5, -562.5, -541.5, -220.5, 220.5, 541.5, 562.5, 283.5, 4.49999999999999, 445.5, 2866.5, 9187.5, -139741.875, -4844.38499999995, -10122.255, -28872.045, -28539.315, -15800.625, -4325.535, -178.605, 178.605, 4325.535, 15800.625, 28539.315, 28872.045, 10122.255, 4844.38500000001, 139741.875), .Dim = c(16L, 4L)), 16, 4, FALSE); .Internal(colSums(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
	}
	@Test
		public void testcolSums8(){
		assertEval("argv <- list(structure(0:1, .Dim = 1:2, .Dimnames = list(\'strata(grp)\', c(\'x\', \'strata(grp)\'))), 1, 2, FALSE); .Internal(colSums(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
	}
	@Test
		public void testcolSums9(){
		assertEval("argv <- list(structure(c(0, 0, 0, 0, 0, -1.43884556914512e-134, 0, 0, 0, -7.95468296571581e-252, 1.76099882882167e-260, 0, -9.38724727098368e-323, -0.738228974836154, 0, 0, 0, 0, 0, 0, 0, 0, 0, -6.84657791618065e-123, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1.05931985100232e-174, 0, -3.41789378681991e-150, 0, 0, 0, 0, -1.07225492686949e-10, 0, 1.65068934474523e-67, 0, -6.49830035279282e-307, 0, 5.83184963977238e-90, 0, -9.81722610183938e-287, 6.25336419454196e-54, 0, 0, 0, -1.72840591500382e-274, 1.22894687952101e-13, 0.660132850077566, 0, 0, 7.79918925397516e-200, -2.73162827952857e-178, 1.32195942051179e-41, 0, 0, 0, 0, 2.036057023761e-45, -3.40425060445074e-186, 1.59974269220388e-26, 0, 6.67054294775317e-124, 0.158503117506202, 0, 0, 0, 0, 0, 0, 3.42455724859116e-97, 0, 0, -2.70246891320217e-272, 0, 0, -3.50562438899045e-06, 0, 0, 1.35101732326608e-274, 0, 0, 0, 0, 0, 0, 0, 7.24580295957621e-65, 0, -3.54887341172294e-149, 0, 0, 0, 0, 0, 0, 0, 0, 1.77584594753563e-133, 0, 0, 0, 2.88385135688311e-250, 1.44299633616158e-259, 0, 1.56124744085834e-321, 1.63995835868977, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2.01050064173383e-122, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.64868196850938e-172, 0, 6.28699823828692e-149, 0, 0, 0, 0, 5.0552295590188e-09, 0, 2.30420733561404e-66, 0, 7.0823279075443e-306, 0, 2.05009901740696e-88, 0, 7.41800724282869e-285, 7.18347043784483e-53, 0, 0, 0, 1.04251223075649e-273, 9.75816316577433e-13, 4.29519957592147, 0, 0, 1.33541454912682e-198, 2.34606233784019e-176, 8.38236726536896e-41, 0, 0, 0, 0, 1.35710537434521e-43, 1.15710503176511e-185, 1.25601735272233e-25, 0, 4.46811655846376e-123, 4.4196641795634, 0, 0, 0, 0, 0, 0, 3.74179015251531e-93, 0, 0, 3.62662047836582e-271, 0, 0, 1.26220330674453e-05, 0, 0, 1.72715562657338e-273, 0, 0, 0, 0, 0, 0, 0, 5.46372806810809e-64, 0, 2.47081972486962e-148, 0, 0, 0), .Dim = c(100L, 2L)), 100, 2, FALSE); .Internal(colSums(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
	}
}
