default: parallel

all:
	python scripts/diffUpdate.py
	mx build 

parallel:
	python scripts/diffUpdate.py  || (echo "mycommand failed $$?"; exit 1)
	mx build -p

update:
	python scripts/marawaccUpdate.py
	mx sforceimports

eclipse:
	mx eclipseinit 

clean:
	mx clean


