# Navigation con rutas tipadas busca por nombre (Class.forName) los enum usados como argumento.
# Si R8 los renombra, la app se cierra al navegar. Hallazgo del build QA (fase 3).
-keep enum com.barberia.feature.auth.domain.model.AppMode { *; }
