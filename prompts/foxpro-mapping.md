FoxPro → Java/JPA translation rules:

Tables & data
- .dbf table → @Entity class, table name mapped to existing DB2 table
- DELETED() filter → @SQLRestriction("deleted = false") or named query
- Character fields padded with spaces → trim() on read; do NOT 
  preserve trailing whitespace in Java strings
- Empty FoxPro date {} → null LocalDate
- Empty FoxPro string "" → may need to remain "" if downstream logic 
  distinguishes from null; flag and ask
- MEMO fields → @Lob String
- Logical (.T./.F.) → boolean primitive

Procedural code
- .prg main procedures → service-layer methods, stateless
- SCAN ... ENDSCAN → JPQL query + stream/forEach; never load whole table
- Implicit work areas / SELECT alias → explicit repository calls
- SET ORDER TO → ORDER BY in JPQL; never rely on insertion order
- PRIVATE/PUBLIC variables → method parameters or CDI bean fields; 
  never static mutable state
- REPLACE ... WITH → entity setter inside @Transactional service
- DO FORM → servlet dispatch + JSP forward
- DO PROCEDURE → service method call

UI (.scx forms)
- Form → one JSP + one servlet (GET render, POST submit)
- TextBox/EditBox → <input>/<textarea> with JSTL value binding
- ComboBox bound to table → JSP iterates List<DTO> from controller
- Grid → <table> with JSTL <c:forEach>; pagination explicit, not auto
- Form .Init() → servlet doGet; .Click() of Save button → doPost
- Form .Refresh() → re-render JSP with updated model

Reports (.frx)
- Out of scope for JSP; convert separately to JasperReports or 
  flag as a follow-up
Step 4: Per-screen prompt template
Convert the FoxPro form @forms/CUSTEDIT.scx (and any .prg it calls) to a 
MicroProfile screen following @prompts/architecture.md and 
@prompts/foxpro-mapping.md.

Use @golden-slice/ as the reference pattern.

Produce:
1. JPA entity updates (if the form touches a table not yet mapped)
2. Repository method(s) needed
3. Service class with business logic from the .prg
4. Servlet (GET + POST handlers)
5. JSP using JSTL only
6. Any DTOs needed for the view
7. Bean Validation annotations matching FoxPro field rules 
   (length, required, range checks from form properties)

For each piece of FoxPro logic you translate, add a `// FOXPRO: <original 
expression>` comment showing what it came from. This makes review 
tractable.

At the end, list:
- Any FoxPro behaviors you couldn't translate cleanly
- Assumptions you made about empty/null/deleted semantics
- Anything that looks like it relies on global FoxPro state