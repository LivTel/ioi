import socket
import logging
class TCPClientSocket:
  '''
  A class to communicate over TCP/IP.
  '''  
  def __init__(self, serverIP, serverPort):
    self.serverIP   = serverIP
    self.serverPort = serverPort
  
  def connectTCPSocket(self):
    '''
    Connect to a TCP/IP socket.
    '''
    self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)    
    server_address = (self.serverIP, self.serverPort)
    try:
      self.sock.connect(server_address)
      logging.info('Connected to socket at ' + self.serverIP + ':' + str(self.serverPort))
      return True
    except socket.error as e:
      logging.critical(e)
      return False

  def closeTCPSocket(self):
    '''
    Disconnect from a TCP/IP socket.
    '''    
    logging.info('Disconnected from socket at ' + self.serverIP + ':' + str(self.serverPort)) 
    self.sock.close()    
