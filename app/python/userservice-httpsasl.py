#!/usr/bin/env python3
#
# userservice-httpsasl
#
# Application code for HTTP SASL on the client end
#
# This can integrate many mechanisms, including Kerberos for
# single-signon, and SXOVER for secure realm crossover.
#
# From: Rick van Rein <rick@openfortress.nl>


import sys
import re
import json
import subprocess


cmd_pinentry = '/usr/local/MacGPG2/libexec/pinentry-mac.app/Contents/MacOS/pinentry-mac'


rex_ok  = 'OK[^\n]*\n'
rex_pin = '^D (?:([A-Z0-9-_]{1,20}):)?(?:([a-z0-9-_+]+)@)?([^\n]+)\n'

re_ok  = re.compile (rex_ok)
re_pin = re.compile (rex_pin)


def pinentry_mech_client_pass (desc, kyfo):
	subcmd = 'SETTIMEOUT 30\nSETDESC %s\nSETPROMPT [MECH:][user@]pass\nSETTITLE Login with HTTP-SASL\nSETKEYINFO %s\nSETOK Login\nSETCANCEL Anonymous\nGETPIN\nBYE\n' % (desc.replace ('\n',' '),kyfo.replace('\n',' '))
	pinentry = subprocess.Popen (executable=cmd_pinentry,args=[],
			stdin=subprocess.PIPE, stdout=subprocess.PIPE,
			stderr=sys.stderr, text=True)
	(outmsg,_) = pinentry.communicate (input=subcmd, timeout=30)
	outmsg = re_ok.sub ('', outmsg)
	m = re_pin.match (outmsg)
	(mech,client,password) = m.groups ()
	return (mech,client,password)
	
	


def process_request (req):
	s2s = req.get ('s2s', None)
	c2s = req.get ('c2s', None)
	realm = req.get ('realm', None)
	mechlist = req.get ('mech', '').split ()
	scheme = None
	user = None
	host = None
	port = None
	uhp = host or 'NO.HOST'
	if user is not None:
		uhp = '%s@%s' % (user,uhp)
	if port is not None:
		uhp = '%s:%d' % (uhp,port)
	author = '%s://%s' % (scheme,uhp)
	desc = 'Passphrase for %s realm %s' % (author,realm or 'DEFAULT')
	kyfo = 'HTTP-SASL for %s realm %s' % (author,realm or 'DEFAULT')
	(mech,clientid,passwd) = pinentry_mech_client_pass (desc, kyfo)
	if mech is not None and mech not in mechlist:
		raise Exception ('Mechanism %s not offered' % (mech,))
	resp = { }
	if s2s is not None:
		resp ['s2s'] = s2s
	if passwd is not None:
		resp ['c2s'] = passwd
	if realm is not None:
		resp ['realm'] = realm
	if mech is not None:
		resp ['mech'] = mech
	if clientid is not None:
		resp ['user'] = clientid
	return resp


lns = ''
while True:
	ln = sys.stdin.readline ()
	lns += ln
	try:
		req = json.loads (lns)
		lns = ''
	except json.decoder.JSONDecodeError:
		print ('Not decoding; need extra line', file=sys.stderr)
		continue
	print ('Request: %r' % (req,), file=sys.stderr)
	try:
		resp = process_request (req)
	except Exception as e:
		print (e, file=sys.stderr)
		resp = { }
	print ('Response: %r' % (resp,), file=sys.stderr)
	print (json.dumps (resp))


