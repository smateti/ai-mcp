* Sample FoxPro program for testing
* Module: Customer Maintenance

PROCEDURE UpdateCustomerName
  PARAMETERS pCustID, pNewName
  SELECT customer
  LOCATE FOR cust_id = pCustID
  IF FOUND() AND NOT DELETED()
    REPLACE cust_name WITH pNewName
    REPLACE last_upd WITH DATE()
    RETURN .T.
  ENDIF
  RETURN .F.
ENDPROC

PROCEDURE GetCustomerBalance
  PARAMETERS pCustID
  SELECT invoices
  SUM amount - paid TO nBalance FOR cust_id = pCustID AND NOT DELETED()
  RETURN nBalance
ENDPROC
