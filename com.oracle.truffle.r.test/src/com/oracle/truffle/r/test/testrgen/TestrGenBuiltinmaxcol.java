package com.oracle.truffle.r.test.testrgen;

import java.util.*;
import org.junit.*;
import com.oracle.truffle.r.test.*;
public class  TestrGenBuiltinmaxcol extends TestBase {
	@Test
		public void testmaxcol1(){
		assertEval("argv <- list(structure(c(80.2, 17, 15, 12, 9.96, 22.2, 83.1, 45.1, 6, 9, 84.84, 22.2, 92.5, 39.7, 5, 5, 93.4, 20.2, 85.8, 36.5, 12, 7, 33.77, 20.3, 76.9, 43.5, 17, 15, 5.16, 20.6, 76.1, 35.3, 9, 7, 90.57, 26.6, 83.8, 70.2, 16, 7, 92.85, 23.6, 92.4, 67.8, 14, 8, 97.16, 24.9, 82.4, 53.3, 12, 7, 97.67, 21, 82.9, 45.2, 16, 13, 91.38, 24.4, 87.1, 64.5, 14, 6, 98.61, 24.5, 64.1, 62, 21, 12, 8.52, 16.5, 66.9, 67.5, 14, 7, 2.27, 19.1, 68.9, 60.7, 19, 12, 4.43, 22.7, 61.7, 69.3, 22, 5, 2.82, 18.7, 68.3, 72.6, 18, 2, 24.2, 21.2, 71.7, 34, 17, 8, 3.3, 20, 55.7, 19.4, 26, 28, 12.11, 20.2, 54.3, 15.2, 31, 20, 2.15, 10.8, 65.1, 73, 19, 9, 2.84, 20, 65.5, 59.8, 22, 10, 5.23, 18, 65, 55.1, 14, 3, 4.52, 22.4, 56.6, 50.9, 22, 12, 15.14, 16.7, 57.4, 54.1, 20, 6, 4.2, 15.3, 72.5, 71.2, 12, 1, 2.4, 21, 74.2, 58.1, 14, 8, 5.23, 23.8, 72, 63.5, 6, 3, 2.56, 18, 60.5, 60.8, 16, 10, 7.72, 16.3, 58.3, 26.8, 25, 19, 18.46, 20.9, 65.4, 49.5, 15, 8, 6.1, 22.5, 75.5, 85.9, 3, 2, 99.71, 15.1, 69.3, 84.9, 7, 6, 99.68, 19.8, 77.3, 89.7, 5, 2, 100, 18.3, 70.5, 78.2, 12, 6, 98.96, 19.4, 79.4, 64.9, 7, 3, 98.22, 20.2, 65, 75.9, 9, 9, 99.06, 17.8, 92.2, 84.6, 3, 3, 99.46, 16.3, 79.3, 63.1, 13, 13, 96.83, 18.1, 70.4, 38.4, 26, 12, 5.62, 20.3, 65.7, 7.7, 29, 11, 13.79, 20.5, 72.7, 16.7, 22, 13, 11.22, 18.9, 64.4, 17.6, 35, 32, 16.92, 23, 77.6, 37.6, 15, 7, 4.97, 20, 67.6, 18.7, 25, 7, 8.65, 19.5, 35, 1.2, 37, 53, 42.34, 18, 44.7, 46.6, 16, 29, 50.43, 18.2, 42.8, 27.7, 22, 29, 58.33, 19.3), .Dim = c(6L, 47L), .Dimnames = list(c(\'Fertility\', \'Agriculture\', \'Examination\', \'Education\', \'Catholic\', \'Infant.Mortality\'), c(\'Courtelary\', \'Delemont\', \'Franches-Mnt\', \'Moutier\', \'Neuveville\', \'Porrentruy\', \'Broye\', \'Glane\', \'Gruyere\', \'Sarine\', \'Veveyse\', \'Aigle\', \'Aubonne\', \'Avenches\', \'Cossonay\', \'Echallens\', \'Grandson\', \'Lausanne\', \'La Vallee\', \'Lavaux\', \'Morges\', \'Moudon\', \'Nyone\', \'Orbe\', \'Oron\', \'Payerne\', \'Paysd'enhaut\', \'Rolle\', \'Vevey\', \'Yverdon\', \'Conthey\', \'Entremont\', \'Herens\', \'Martigwy\', \'Monthey\', \'St Maurice\', \'Sierre\', \'Sion\', \'Boudry\', \'La Chauxdfnd\', \'Le Locle\', \'Neuchatel\', \'Val de Ruz\', \'ValdeTravers\', \'V. De Geneve\', \'Rive Droite\', \'Rive Gauche\'))), 1L); .Internal(max.col(argv[[1]], argv[[2]]))");
	}
	@Test
		public void testmaxcol2(){
		assertEval("argv <- list(structure(c(0.0140185568997224, 0.0152950000405453, 0.013933189413423, 0.0141545247888734, 0.0144656253644622, 0.0138841285101751, 0.014995293880605, 0.0135531935867069, 0.0136464824425927, 0.0139216121812551, 0.0150015663096977, 0.0141230892456885, 0.013614290948012, 0.0169498879707739, 0.0167919904349555, 0.0164281655519131, 0.0145450130659148, 0.0154935322596005, 0.0140566103751186, 0.0137862479562347, 0.0138916844301758, 0.0144143878263478, 0.0153699249520618, 0.0156955405518398, 0.0143684930741837, 0.991123944314599, 0.772371856665358, 0.998388573397845, 0.997744681008954, 0.935000528851613, 0.995759465226583, 0.998319991897437, 0.998446741491899, 0.997291233910865, 0.998453850443283, 0.449550979690061, 0.99765492178392, 0.0744670889060699, 0.997953251276348, 0.998367231220745, 0.998303801028119, 0.996490251221358, 0.987068752837462, 0.963362766144961, 0.997745619693091, 0.998180187351627, 0.995230116685749, 0.99834615324817, 0.998224606721368, 0.998138182928866, 0.000101796455995556, 0.0169548122668949, 0.00010041243364593, 0.994976075194857, 0.000116262428296307, 0.000266333816370553, 0.000213420942072358, 0.000150164062855871, 9.91852669694001e-05, 0.962237984681016, 0.000109709045973819, 0.363503438381572, 0.000165884012322677, 0.000404227768170316, 0.000101407372837694, 0.000138346452367636, 0.76867700377653, 0.000101067307394145, 0.000798310678132636, 0.439735407970107, 0.000105640126458538, 0.000103729730504055, 0.000157422658078269, 0.00062878104546921, 0.000140302481934868, 0.987636544924171, 0.986762198204236, 0.987695606647598, 0.987542563977846, 0.987328468487257, 0.987729584212166, 0.986966061808917, 0.987959390267427, 0.987894530108167, 0.987703622276188, 0.986961786481457, 0.987564327481863, 0.987916920251847, 0.98565103396999, 0.98575611086066, 0.985998830615913, 0.98727397408636, 0.986627618096195, 0.987610242071539, 0.987797448350422, 0.987724349638781, 0.987363673212559, 0.986711269247982, 0.986491053812255, 0.987395229430566, 0.0127450421932153, 0.00673790924500044, 0.0168765170487183, 0.015797380803532, 0.00875985277873091, 0.0142537568101031, 0.0172964637554702, 0.0177648866573519, 0.0158550778308362, 0.0172334564486378, 0.00522951225361075, 0.016267073149734, 0.00347221059583105, 0.0218803200901225, 0.0183403081414579, 0.0180163362514856, 0.0146261930363668, 0.0119682371438135, 0.00971509310832369, 0.0157071233034631, 0.017455515535567, 0.0139105878597395, 0.0174050248646065, 0.0173796025035352, 0.0168918350504782, 0.00106971573173608, 0.0026383344434856, 0.00106703814487522, 0.0135614845327103, 0.0010949673490627, 0.00126684800065677, 0.0012190851300456, 0.00114670950680761, 0.00106469628452917, 0.00946684926508704, 0.00108427378412549, 0.00489096395354091, 0.00116581741675497, 0.00136406369196257, 0.00106938597766297, 0.00112914854449728, 0.00664571845549644, 0.00106837166942789, 0.00153810249624049, 0.0051794966429432, 0.00107683746869901, 0.00107356047093305, 0.00115632815053843, 0.001475874716352, 0.00113310775095649, 0.000705529701133523, 0.000706190813132159, 0.000705483416292851, 0.000705607277564461, 0.000705767694047911, 0.000705456690994395, 0.000706040550884142, 0.000705277731844386, 0.000705325918720134, 0.00070547711802582, 0.000706043725519247, 0.000705586003991082, 0.000705308033747408, 0.000706976814055453, 0.000706900888924168, 0.000706734153004456, 0.000705809204506355, 0.000706288779684405, 0.000705550244606539, 0.000705403095546089, 0.000705460812978617, 0.000705740784771567, 0.000706233802920496, 0.000706387215078423, 0.000705716602186515, 0.00537527373619432, 0.193553056279976, 0.000869791621482113, 0.00126068143747944, 0.0477132994644455, 0.00247011263414166, 0.000876993026210466, 0.000793804652755058, 0.00147446124252569, 0.000818798505743392, 0.527720370257185, 0.0012613575859543, 0.931485133910046, 0.000794860447953985, 0.000799403966921179, 0.000843774285071599, 0.00203097055872496, 0.00804383321163345, 0.0255537088264535, 0.00126855734163029, 0.000930853589102135, 0.00281671019786704, 0.000858777960111907, 0.000915470358337216, 0.000986308498091386, 0.999944492377256, 0.98624753604171, 0.999945310582066, 0.00303527549384713, 0.999935958318038, 0.99984366374275, 0.999876760118408, 0.999915533835607, 0.999946031942947, 0.0270168111120999, 0.999939809617296, 0.622685795280626, 0.999906081646851, 0.999754847875723, 0.999944697838299, 0.999922757726417, 0.198924025871316, 0.99994491987507, 0.99948964681356, 0.539122196215121, 0.999942224996369, 0.999943338667082, 0.999911124821608, 0.999605022779117, 0.999921489451661), .Dim = c(75L, 3L), .Dimnames = list(    NULL, c(\'c\', \'s\', \'v\'))), 1L); .Internal(max.col(argv[[1]], argv[[2]]))");
	}
	@Test
		public void testmaxcol3(){
		assertEval("argv <- list(structure(list(), .Dim = 0:1), 1L); .Internal(max.col(argv[[1]], argv[[2]]))");
	}
}
