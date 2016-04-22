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

clean:
	mx clean


