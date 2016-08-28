
function saxpy {
	# Saxpy 8M
	./runbench benchmarks/fastR/saxpy/saxpy.R 1048576 > saxpyFASTR.log 
	./runbench benchmarks/fastR/saxpy/saxpyGPUPArrays.R 1048576 > saxpyASTx.log
	$GNUR/Rscript benchmarks/fastR/saxpy/saxpyGNU.R 1048576 > saxpyGNU.log
}

function bs1M {
	# BS 1M
	./runbench benchmarks/fastR/blackscholes/blackscholes.R 1048576 > bsFASTR.log 
	./runbench benchmarks/fastR/blackscholes/blackscholesSeq.R 1048576 > bsSeq.log
	./runbench benchmarks/fastR/blackscholes/blackscholesGPUPArrays.R 1048576 > bsASTx.log
	$GNUR/Rscript benchmarks/fastR/blackscholes/blackscholesGNU.R 1048576 > bsGNU.log
}

function bs4M {
	# BS 4M
	./runbench benchmarks/fastR/blackscholes/blackscholes.R 4194304 > bsFASTR4.log 
	./runbench benchmarks/fastR/blackscholes/blackscholesGPUPArrays.R 4194304 > bsASTx4.log
	./runbench benchmarks/fastR/blackscholes/blackscholesSeq.R 4194304 > bsSeq4.log
	$GNUR/Rscript benchmarks/fastR/blackscholes/blackscholesGNU.R 4194304 > bsGNU4.log
}

function nbody {
	# NBody 65536
	./runbench benchmarks/fastR/nbody/nbody.R 65536 > nbodyFastR.log
	./runbench benchmarks/fastR/nbody/nbodySeq.R 65536 > nbodySeq.log
	./runbench benchmarks/fastR/nbody/nbodyGPUPArrays.R 65536 > nbodyASTx.log
	$GNUR/Rscript benchmarks/fastR/nbody/nbodyGNU.R 65536 > nbodyGNU.log
}

function dft() {
	./runbench benchmarks/fastR/dft/dft.R 8192 > dftFastR.log
	./runbench benchmarks/fastR/dft/dftSeq.R 8192 > dftSeq.log
	./runbench benchmarks/fastR/dft/dftGPUPArrays.R 8192 > dftASTx.log
	$GNUR/Rscript benchmarks/fastR/dft/dftGNU.R 8192 > dftGNU.log
}

function mandelbrot() {
	./runbench benchmarks/fastR/mandelbrot/mandelbrot.R 1024 > mandelbrotFastR.log
	./runbench benchmarks/fastR/mandelbrot/mandelbrotSeq.R 1024 > mandelbrotSeq.log
	./runbench benchmarks/fastR/mandelbrot/mandelbrotGPUPArrays.R 1024 > mandelbrotASTx.log
	$GNUR/Rscript benchmarks/fastR/mandelbrot/mandelbrotGNU.R 1024 > mandelbrotGNU.log
}

function montecarlo() {
	./runbench benchmarks/fastR/montecarlo/montecarlo.R  262144 > montecarloFastR.log
	./runbench benchmarks/fastR/montecarlo/montecarloSeq.R  262144 > montecarloSeq.log
	./runbench benchmarks/fastR/montecarlo/montecarloGPUPArrays.R  262144 > montecarloASTx.log
	$GNUR/Rscript benchmarks/fastR/montecarlo/montecarloGNU.R 262144 > montecarloGNU.log
}

## main
saxpy
bs1M
bs4M
nbody
dft
mandelbrot
montecarlo
