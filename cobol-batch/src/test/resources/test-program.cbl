       IDENTIFICATION DIVISION.
       PROGRAM-ID. TESTPROG.
       AUTHOR. TEST AUTHOR.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ACCT-FILE ASSIGN TO ACCTFILE.

       DATA DIVISION.
       FILE SECTION.
       FD ACCT-FILE.
       01  ACCT-RECORD.
           05  ACCT-ID         PIC X(10).
           05  ACCT-NAME       PIC X(30).

       WORKING-STORAGE SECTION.
       01  WS-TRANSACTION-REC.
           05  WS-TRAN-ID      PIC X(10).
           05  WS-TRAN-AMT     PIC 9(7)V99.
           05  WS-TRAN-TYPE    PIC X(1).
               88  WS-CREDIT   VALUE 'C'.
               88  WS-DEBIT    VALUE 'D'.
           05  WS-TRAN-STATUS  PIC X(1).
               88  WS-APPROVED VALUE 'A'.
               88  WS-REJECTED VALUE 'R'.

       01  WS-FLAGS.
           05  WS-EOF          PIC X(1) VALUE 'N'.
               88  END-OF-FILE VALUE 'Y'.

       PROCEDURE DIVISION.
       MAIN-PROGRAM.
           PERFORM INIT-PROGRAM.
           PERFORM PROCESS-TRANSACTION.
           COPY COMMON.
           STOP RUN.

       INIT-PROGRAM.
           EXEC CICS
               SEND MAP('MAINMAP')
               FROM(MAIN-AREA)
           END-EXEC.
           CALL 'SUBPROG1'.
           CALL WS-DYNAMIC-NAME.

       PROCESS-TRANSACTION.
           EXEC SQL
               SELECT BALANCE, LIMIT
               FROM ACCOUNTS
               WHERE ACCT_ID = :WS-TRAN-ID
           END-EXEC.
           IF WS-TRAN-AMT > WS-CREDIT-LIMIT
               MOVE 'R' TO WS-TRAN-STATUS
           END-IF.
           COMPUTE WS-NEW-BALANCE =
               WS-BALANCE + WS-TRAN-AMT.

       ERROR-HANDLER.
           EXEC IDMS
               BIND RUN-UNIT
           END-EXEC.
