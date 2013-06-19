from SIDECARComm import SIDECARTCPClientSocket
import argparse  
import logging  
if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument('-ip', action='store', default='ioi', dest='ip', help='IP of SIDECAR ASIC IDL socket server')
  parser.add_argument('-port', action='store', default='5000', dest='port', help='Port of SIDECAR ASIC IDL socket server')
  parser.add_argument('-log', action='store', default='DEBUG', dest='logLevel', help='Level of logging')
  parser.add_argument('-on', action='store', default=None, dest='initLevel', help='Initialise SIDECAR ASIC')
  parser.add_argument('-off', action='store_true', default=False, dest='deinit', help='Deinitialise SIDECAR ASIC')
  parser.add_argument('-cmd', action='store', default=None, nargs='+', dest='cmd', help='Command to execute on SIDECAR ASIC')
  parser.add_argument('-listcmd', action='store_true', default='False', dest='listCmd', help='List available SIDECAR ASIC commands')
 
  args = parser.parse_args()
  ip		= str(args.ip)
  port		= int(args.port)
  initLevel	= None if type(args.initLevel).__name__ == 'NoneType' else int(args.initLevel)
  deinit	= args.deinit
  logLevel	= str(args.logLevel)
  listCmd	= args.listCmd
  cmd 		= None if type(args.cmd).__name__ == 'NoneType' else list(args.cmd)

  logging.basicConfig(format='%(levelname)s: %(message)s', level=getattr(logging, logLevel.upper()))
  
  SIDECAR = SIDECARTCPClientSocket(ip, port)
  # -listcmd flag
  if listCmd is True:
    SIDECAR.aux_listCommands()
  elif SIDECAR.connectTCPSocket():
    # -on flag
    if initLevel is not None:
      SIDECAR.cmdChain_ASICON(initLevel)

    # -cmd flag
    if cmd is not None:
      try:
        res = getattr(SIDECAR, 'cmd_'+cmd[0].upper())(*cmd[1:])
      except AttributeError as e:
        logging.critical(e)
      except TypeError as e:
        logging.critical(e)

    # -off flag
    if deinit is True:
      SIDECAR.cmd_POWERDOWNASIC()

    SIDECAR.closeTCPSocket()

  exit(0)


