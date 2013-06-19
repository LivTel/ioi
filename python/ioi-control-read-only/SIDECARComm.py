import logging
import re
import inspect
from TCPSocket import TCPClientSocket
class SIDECARTCPClientSocket(TCPClientSocket):
  '''
  A class to communicate over TCP/IP with the SIDECAR ASIC's IDL socket server.
  '''
  def __init__(self, serverIP, serverPort):
    TCPClientSocket.__init__(self, serverIP, serverPort) # explicitly call overridden constructor
    self.configDict = {}
    self.ASICPowerDict = {}
    
  def sendTCPMessage(self, cmdStr, encoding, *args):
    '''
    Base function for sending TCP/IP messages to the SIDECAR ASIC socket server.
    '''    
    argumentStr = ''
    if (len(args) > 0): # are there parameters to this function call?
      argumentStr += '('
      for param in args:
        if type(param).__name__ != 'str':
          argumentStr += str(param)
        else:
          argumentStr += param
        argumentStr += ', '
        
      argumentStr = argumentStr.rstrip(', ')
      argumentStr += ')'
      
    cmdStr += argumentStr      
    cmdBytes = bytes(cmdStr, encoding) 	# convert cmd string to bytes
    self.sock.sendall(cmdBytes)		# send through socket
    logging.info('Sent command: "%s"' % cmdStr + ' to ' + self.serverIP + ':' + str(self.serverPort))

  def receiveTCPMessage(self, bufferSize, encoding):
    '''
    Base function for receiving and parsing TCP/IP messages sent from the SIDECAR ASIC socket server.
    '''     
    rtnByt = self.sock.recv(bufferSize)
    rtnStr = rtnByt.decode(encoding)
    rtnDict = {}  
    if rtnStr.endswith(b'\r\n'.decode(encoding)): 	# check received all data (ends with '\r\n')
      rtnStr = rtnStr.rstrip('\r\n')			# strip carriage return/newline
      if rtnStr.find(":") >= 0: 			# IF return has "errCode:errString" format
        rntList = rtnStr.split(':')
        rtnDict['rtnCode'] = int(rntList[0])
        rtnDict['rtnMsg'] = str(rntList[1])     
      else: 						# ELSE treat return as one string of recursive format "key=value"
        rtnStr = rtnStr.replace(' ', '')
        regex = re.compile("[a-zA-Z0-9_]*=[+-]?[a-z]?[0-9.]*")
        rtnList = regex.findall(rtnStr) # returns a list of "key=value" elements
        for element in rtnList:
          rtnDict[element.split('=')[0]] = element.split('=')[1] # make list into dictionary of format key:value
        rtnDict['rtnCode'] = 0
        rtnDict['rtnMsg'] = 'successful'       
    else:
      rtnDict['rtnCode'] = -2
      rtnDict['rtnMsg'] = 'failed. buffer overun.'       
      logging.error('Buffer overrun')
    logging.info('Command returned code:\t' + '"' + str(rtnDict['rtnCode']) + '"')
    logging.info('Command returned msg:\t' + '"' + rtnDict['rtnMsg'] + '"')   
    return rtnDict
      
  '''
  Individual socket commands.
  (Refer to socket server command reference document)
  '''
  def cmd_PING(self):
    '''
    Check the status of system.
    '''        
    self.sendTCPMessage('PING', 'UTF-8')
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0 or rtnDict['rtnCode'] == -1:
      logging.info('"PING" command succeeded')
      return True
    else:
      logging.error('"PING" command failed')
      return False

  def cmd_GETCONFIG(self):
    '''
    Check the status of system.
    '''        
    self.sendTCPMessage('GETCONFIG', 'UTF-8')
    rtnDict = self.receiveTCPMessage(1024, 'UTF-8')
    if rtnDict['rtnCode'] == 0 or rtnDict['rtnCode'] == -1: 
      self.configDict = rtnDict
      logging.info('"GETCONFIG" command succeeded')
      logging.debug(self.configDict)
      return True
    else:
      logging.error('"GETCONFIG" command failed')
      return False    

  def cmd_GETASICPOWER(self):
    '''
    Check voltages and currents for the SIDECAR ASIC board. 
    '''        
    self.sendTCPMessage('GETASICPOWER', 'UTF-8')
    rtnDict = self.receiveTCPMessage(1024, 'UTF-8')
    if rtnDict['rtnCode'] == 0 or rtnDict['rtnCode'] == -1: 
      self.ASICPowerDict = rtnDict
      logging.info('"GETASICPOWER" command succeeded')
      logging.debug(self.ASICPowerDict)
      return True
    else:
      logging.error('"GETASICPOWER" command failed')
      return False  

  def cmd_INITIALIZE1(self):
    '''
    Initialise the SIDECAR ASIC (level 1).
    (Performs no other actions.)
    '''       
    self.sendTCPMessage('INITIALIZE1', 'UTF-8')
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0:
      logging.info('"INITIALIZE1" command succeeded')
      return True
    else:
      logging.error('"INITIALIZE1" command failed')
      return False

  def cmd_INITIALIZE2(self):
    '''
    Initialise the SIDECAR ASIC (level 2).
    (Downloads JADE2 firmware and JADE2 registers.)
    '''       
    self.sendTCPMessage('INITIALIZE2', 'UTF-8')
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0:
      logging.info('"INITIALIZE2" command succeeded')
      return True
    else:
      logging.error('"INITIALIZE2" command failed')
      return False

  def cmd_INITIALIZE3(self):
    '''
    Initialise the SIDECAR ASIC (level 3).
    (Launches SIDECAR ASIC IDE, downloads JADE2 firmware and
    JADE2 registers, resets the SIDECAR ASIC and downloads
    default .MCD file.)
    '''      
    self.sendTCPMessage('INITIALIZE3', 'UTF-8')
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0:
      logging.info('"INITIALIZE3" command succeeded')
      return True
    else:
      logging.error('"INITIALIZE3" command failed')
      return False

  def cmd_POWERUPASIC(self):
    '''
    Powers up the SIDECAR ASIC.
    '''      
    self.sendTCPMessage('POWERUPASIC', 'UTF-8')
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0:
      logging.info('"POWERUPASIC" command succeeded')
      return True
    else:
      logging.error('"POWERUPASIC" command failed')
      return False

  def cmd_POWERDOWNASIC(self):
    '''
    Powers down SIDECAR ASIC.
    '''       
    self.sendTCPMessage('POWERDOWNASIC', 'UTF-8')
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0:
      logging.info('"POWERDOWNASIC" command succeeded')
      return True
    else:
      logging.error('"POWERDOWNASIC" command failed')
      return False

  def cmd_RESETASIC(self):
    '''
    Resets the SIDECAR ASIC registers to default state.
    '''    
    self.sendTCPMessage('RESETASIC', 'UTF-8')
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0:
      logging.info('"RESETASIC" command succeeded')
      return True
    else:
      logging.error('"RESETASIC" command failed')
      return False

  def cmd_DOWNLOADDEFAULTMCDFILE(self):
    '''
    Downloads default .MCD to the SIDECAR ASIC.
    (Presumably "default" is that set in "LOAD MCD" field of IDL
    interface.)
    '''
    self.sendTCPMessage('DOWNLOADMCD', 'UTF-8')
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0:
      logging.info('"DOWNLOADMCD" command succeeded')
      return True
    else:
      logging.error('"DOWNLOADMCD" command failed')
      return False

  def cmd_DOWNLOADMCDFILE(self, MCDfile):
    '''
    Downloads specified [path/to/MCDFile] .MCD to SIDECAR ASIC.
    '''
    self.sendTCPMessage('DOWNLOADMCDFILE', 'UTF-8', MCDfile)
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0:
      logging.info('"DOWNLOADMCDFILE" command succeeded')
      return True
    else:
      logging.error('"DOWNLOADMCDFILE" command failed')
      return False   

  def cmd_ACQUIRESINGLEFRAME(self):
    '''
    Acquires a single frame in UTR mode. If system is configured
    for Fowler sampling, returns error code 1.
    '''
    self.sendTCPMessage('ACQUIRESINGLEFRAME', 'UTF-8')
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0:
      logging.info('"ACQUIRESINGLEFRAME" command succeeded')
      return True
    else:
      logging.error('"ACQUIRESINGLEFRAME" command failed')
      return False    

  def cmd_SETWINPARAMS(self, x1, x2, y1, y2):
    '''
    Set window mode parameter (x1, x2, y1, y2). Each input should
    be in the range [0 -> 2047].
    '''
    self.sendTCPMessage('SETWINPARAMS', 'UTF-8', x1, x2, y1, y2)
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0:
      logging.info('"SETWINPARAMS" command succeeded')
      return True
    else:
      logging.error('"SETWINPARAMS" command failed')
      return False  

  def cmd_SETWINDOWMODE(self, n):
    '''
    Set mode of data capture. 1=window, 0=full frame.
    '''
    self.sendTCPMessage('SETWINDOWMODE', 'UTF-8', n)
    rtnDict = self.receiveTCPMessage(64, 'UTF-8')
    if rtnDict['rtnCode'] == 0:
      logging.info('"SETWINDOWMODE" command succeeded')
      return True
    else:
      logging.error('"SETWINDOWMODE" command failed')
      return False  
    
  '''
  Useful command chains.
  '''
  def cmdChain_ASICON(self, initLevel):
    '''
    Perform an initialisation of the SIDECAR ASIC.
    '''
    logging.disable(logging.CRITICAL) # temporarily disable logging (CRITICAL and below) to establish state of system
    state = self.cmd_PING()
    logging.disable(logging.NOTSET)
    if state is False:
      if initLevel == 1:
        if self.cmd_INITIALIZE1():
          if self.cmd_POWERUPASIC():
            if self.cmd_RESETASIC():
              if self.cmd_DOWNLOADDEFAULTMCDFILE():
                return True
      elif initLevel == 2:
        if self.cmd_INITIALIZE2():
          if self.cmd_POWERUPASIC():
            if self.cmd_RESETASIC():
              if self.cmd_DOWNLOADDEFAULTMCDFILE():
                return True
      elif initLevel == 3:
        if self.cmd_INITIALIZE3():
          return True            
      else:
        logging.critical("initLevel must be either 1, 2 or 3.")
        return False
    else:
      logging.info('SIDECAR ASIC is already initialised. "-on" flag ignored.')
      return True

  '''
  Aux. commands.
  '''
  def aux_listCommands(self):
    commandList = []
    logging.info('List of available commands:')
    for key in inspect.getmembers(self):
      if(key[0].find("cmd_") >= 0):
        commandList.append(key[0].split("cmd_")[1])
    logging.info(commandList)

