
function saxpy {
	# Saxpy 8M
	./runbench benchmarks/fastR/saxpy/saxpy.R 8388608 > saxpyFASTR.log 
	./runbench benchmarks/fastR/saxpy/saxpySeq.R 8388608 > saxpySeq.log
	./runbench benchmarks/fastR/saxpy/saxpyGPU.R 8388608 > saxpyASTxFull.log
	./runbench benchmarks/fastR/saxpy/saxpyGPUPArrays.R 8388608 > saxpyASTx.log
	$GNUR/Rscript benchmarks/fastR/saxpy/saxpyGNU.R 8388608 > saxpyGNU.log
}

function bs {
	# BS 1M
	./runbench benchmarks/fastR/blackscholes/blackscholes.R 1048576 > bsFASTR.log 
	./runbench benchmarks/fastR/blackscholes/blackscholesSeq.R 1048576 > bsSeq.log
	./runbench benchmarks/fastR/blackscholes/blackscholesGPU.R 1048576 > bsASTxFull.log
	./runbench benchmarks/fastR/blackscholes/blackscholesGPUPArrays.R 1048576 > bsASTx.log
	$GNUR/Rscript benchmarks/fastR/blackscholes/blackscholesGNU.R 1048576 > bsGNU.log
}

function nbody {
	# NBody 65536
	./runbench benchmarks/fastR/nbody/nbody.R 65536 > nbodyFastR.log
	./runbench benchmarks/fastR/nbody/nbodySeq.R 65536 > nbodySeq.log
	./runbench benchmarks/fastR/nbody/nbodyGPU.R 65536 > nbodyASTxFull.log
	./runbench benchmarks/fastR/nbody/nbodyGPUPArrays.R 65536 > nbodyASTx.log
	$GNUR/Rscript benchmarks/fastR/nbody/nbodyGNU.R 65536 > nbodyGNU.log
}

function dft() {
	./runbench benchmarks/fastR/dft/dft.R 8192 > dftFastR.log
	./runbench benchmarks/fastR/dft/dftSeq.R 8192 > dftSeq.log
	./runbench benchmarks/fastR/dft/dftGPU.R 8192 > dftASTxFull.log
	./runbench benchmarks/fastR/dft/dftGPUPArrays.R 8192 > dftASTx.log
	$GNUR/Rscript benchmarks/fastR/dft/dftGNU.R 8192 > dftGNU.log
}

function mandelbrot() {
	./runbench benchmarks/fastR/mandelbrot/mandelbrot.R 1024 > mandelbrotFastR.log
	./runbench benchmarks/fastR/mandelbrot/mandelbrotSeq.R 1024 > mandelbrotSeq.log
	./runbench benchmarks/fastR/mandelbrot/mandelbrotGPU.R 1024 > mandelbrotASTxFull.log
	./runbench benchmarks/fastR/mandelbrot/mandelbrotGPUPArrays.R 1024 > mandelbrotASTx.log
	$GNUR/Rscript benchmarks/fastR/mandelbrot/mandelbrotGNU.R 1024 > mandelbrotGNU.log
}

function montecarlo() {
	./runbench benchmarks/fastR/montecarlo/montecarlo.R  262144 > montecarloFastR.log
	./runbench benchmarks/fastR/montecarlo/montecarloSeq.R  262144 > montecarloSeq.log
	./runbench benchmarks/fastR/montecarlo/montecarloGPU.R  262144 > montecarloASTxFull.log
	./runbench benchmarks/fastR/montecarlo/montecarloGPUPArrays.R  262144 > montecarloASTx.log
	$GNUR/Rscript benchmarks/fastR/montecarlo/montecarloGNU.R 262144 > montecarloGNU.log
}

function kmeans() {
	./runbench benchmarks/fastR/kmeans/kmeans.R 4194304 > kmeansFastR.log
	./runbench benchmarks/fastR/kmeans/kmeansSeq.R 4194304 > kmeansSeq.log
	./runbench benchmarks/fastR/kmeans/kmeansGPU.R 4194304 > kmeansASTxFull.log
	./runbench benchmarks/fastR/kmeans/kmeansGPUPArrays.R 4194304 > kmeansASTx.log
	$GNUR/Rscript benchmarks/fastR/kmeans/kmeansGNU.R 4194304 > kmeansGNU.log
}

function hilbert() {
	./runbench benchmarks/fastR/hilbert/hilbert.R 4096 > hilbertFastR.log
	./runbench benchmarks/fastR/hilbert/hilbertSeq.R 4096 > hilbertSeq.log
	./runbench benchmarks/fastR/hilbert/hilbertGPU.R 4096 > hilbertASTxFull.log
	./runbench benchmarks/fastR/hilbert/hilbertGPUPArrays.R 4096 > hilbertASTx.log
	$GNUR/Rscript benchmarks/fastR/hilbert/hilbertGNU.R 4096 > hilbertGNU.log
}

function spectralNorm() {
	./run benchmarks/fastR/spectralNorm/spectralNorm.R 32768 > spectralNormFastR.log
	./run benchmarks/fastR/spectralNorm/spectralNormSeq.R 32768 > spectralNormSeq.log
	./run benchmarks/fastR/spectralNorm/spectralNormGPU.R 32768 > spectralNormASTxFull.log
	./run benchmarks/fastR/spectralNorm/spectralNormGPUPArrays.R 32768 > spectralNormASTx.log
	$GNUR/Rscript benchmarks/fastR/spectralNorm/spectralNormGNU.R 32768 > spectralNormGNU.log
}

## main
saxpy
bs
nbody
dft
mandelbrot
montecarlo
kmeans
hilbert
spectralNorm
