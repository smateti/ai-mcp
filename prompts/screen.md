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