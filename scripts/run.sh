
function saxpy {
	echo "SAXPY"
	size=8388608
	#./runbench benchmarks/fastR/saxpy/saxpy.R $size > $DIRECTORY/saxpyFASTR.log 
	#./runbench benchmarks/fastR/saxpy/saxpySeq.R $size > $DIRECTORY/saxpySeq.log
	./runbench benchmarks/fastR/saxpy/saxpyGPU.R $size > $DIRECTORY/saxpyASTxFull.log
	#./runbench benchmarks/fastR/saxpy/saxpyGPUPArrays.R $size > $DIRECTORY/saxpyASTx.log
	#$GNUR/Rscript benchmarks/fastR/saxpy/saxpyGNU.R $size > $DIRECTORY/saxpyGNU.log
}

function bs {
	echo "BS"
	size=1048576
	#./runbench benchmarks/fastR/blackscholes/blackscholes.R $size > $DIRECTORY/bsFASTR.log 
	#./runbench benchmarks/fastR/blackscholes/blackscholesSeq.R $size > $DIRECTORY/bsSeq.log
	./runbench benchmarks/fastR/blackscholes/blackscholesGPU.R $size > $DIRECTORY/bsASTxFull.log
	#./runbench benchmarks/fastR/blackscholes/blackscholesGPUPArrays.R $size > $DIRECTORY/bsASTx.log
	#$GNUR/Rscript benchmarks/fastR/blackscholes/blackscholesGNU.R $size > $DIRECTORY/bsGNU.log
}

function nbody {
	echo "NBODY"
	size=65536
	#./runbench benchmarks/fastR/nbody/nbody.R $size > $DIRECTORY/nbodyFastR.log
	#./runbench benchmarks/fastR/nbody/nbodySeq.R $size > $DIRECTORY/nbodySeq.log
	./runbench benchmarks/fastR/nbody/nbodyGPU.R $size > $DIRECTORY/nbodyASTxFull.log
	#./runbench benchmarks/fastR/nbody/nbodyGPUPArrays.R $size > $DIRECTORY/nbodyASTx.log
	#$GNUR/Rscript benchmarks/fastR/nbody/nbodyGNU.R $size > $DIRECTORY/nbodyGNU.log
}

function dft() {
	echo "DFT"
	size=8192
	#./runbench benchmarks/fastR/dft/dft.R $size > $DIRECTORY/dftFastR.log
	#./runbench benchmarks/fastR/dft/dftSeq.R $size > $DIRECTORY/dftSeq.log
	./runbench benchmarks/fastR/dft/dftGPU.R 8192 > $DIRECTORY/dftASTxFull.log
	#./runbench benchmarks/fastR/dft/dftGPUPArrays.R $size > $DIRECTORY/dftASTx.log
	#$GNUR/Rscript benchmarks/fastR/dft/dftGNU.R $size > $DIRECTORY/dftGNU.log
}

function mandelbrot() {
	echo "MANDELBROT"
	size=1024
	#./runbench benchmarks/fastR/mandelbrot/mandelbrot.R $size > $DIRECTORY/mandelbrotFastR.log
	#./runbench benchmarks/fastR/mandelbrot/mandelbrotSeq.R $size > $DIRECTORY/mandelbrotSeq.log
	./runbench benchmarks/fastR/mandelbrot/mandelbrotGPU.R $size > $DIRECTORY/mandelbrotASTxFull.log
	#./runbench benchmarks/fastR/mandelbrot/mandelbrotGPUPArrays.R $size > $DIRECTORY/mandelbrotASTx.log
	#$GNUR/Rscript benchmarks/fastR/mandelbrot/mandelbrotGNU.R $size > $DIRECTORY/mandelbrotGNU.log
}

function montecarlo() {
	echo "MONTECARLO"
	size=262144
	#./runbench benchmarks/fastR/montecarlo/montecarlo.R  $size > $DIRECTORY/montecarloFastR.log
	#./runbench benchmarks/fastR/montecarlo/montecarloSeq.R  $size > $DIRECTORY/montecarloSeq.log
	./runbench benchmarks/fastR/montecarlo/montecarloGPU.R  $size > $DIRECTORY/montecarloASTxFull.log
	#./runbench benchmarks/fastR/montecarlo/montecarloGPUPArrays.R  $size > $DIRECTORY/montecarloASTx.log
	#$GNUR/Rscript benchmarks/fastR/montecarlo/montecarloGNU.R $size > $DIRECTORY/montecarloGNU.log
}

function kmeans() {
	echo "KMEANS"
	size=4194304
	#./runbench benchmarks/fastR/kmeans/kmeans.R $size > $DIRECTORY/kmeansFastR.log
	#./runbench benchmarks/fastR/kmeans/kmeansSeq.R $size > $DIRECTORY/kmeansSeq.log
	./runbench benchmarks/fastR/kmeans/kmeansGPU.R $size > $DIRECTORY/kmeansASTxFull.log
	#./runbench benchmarks/fastR/kmeans/kmeansGPUPArrays.R $size > $DIRECTORY/kmeansASTx.log
	#$GNUR/Rscript benchmarks/fastR/kmeans/kmeansGNU.R $size > $DIRECTORY/kmeansGNU.log
}

function hilbert() {
	echo "HILBERT"
	size=4096
	#./runbench benchmarks/fastR/hilbert/hilbert.R $size > $DIRECTORY/hilbertFastR.log
	#./runbench benchmarks/fastR/hilbert/hilbertSeq.R $size > $DIRECTORY/hilbertSeq.log
	./runbench benchmarks/fastR/hilbert/hilbertGPU.R $size > $DIRECTORY/hilbertASTxFull.log
	#./runbench benchmarks/fastR/hilbert/hilbertGPUPArrays.R $size > $DIRECTORY/hilbertASTx.log
	#$GNUR/Rscript benchmarks/fastR/hilbert/hilbertGNU.R $size > $DIRECTORY/hilbertGNU.log
}

function spectralNorm() {
	echo "SPECTRAL NORM"
	size=32768
	#./runbench benchmarks/fastR/spectralNorm/spectralNorm.R $size > $DIRECTORY/spectralNormFastR.log
	#./runbench benchmarks/fastR/spectralNorm/spectralNormSeq.R $size > $DIRECTORY/spectralNormSeq.log
	./runbench benchmarks/fastR/spectralNorm/spectralNormGPU.R $size > $DIRECTORY/spectralNormASTxFull.log
	#./runbench benchmarks/fastR/spectralNorm/spectralNormGPUPArrays.R $size > $DIRECTORY/spectralNormASTx.log
	#$GNUR/Rscript benchmarks/fastR/spectralNorm/spectralNormGNU.R $size > $DIRECTORY/spectralNormGNU.log
}

DIRECTORY=`date +%Y-%m-%d_%H_%M_%S`

mkdir $DIRECTORY

## Run the benchmarks
saxpy
bs
nbody
dft
mandelbrot
#montecarlo
kmeans
hilbert
spectralNorm
