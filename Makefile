default: parallel

all:
	python scripts/diffUpdate.py
	mx build 

parallel:
	mx build -p

update:
	mx sforceimports

eclipse:
	mx eclipseinit 

sample:
	./run examples/map.R 

clean:
	mx clean


