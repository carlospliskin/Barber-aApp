import 'package:freezed_annotation/freezed_annotation.dart';

part 'auth_failure.freezed.dart';

/// Errores de autenticación modelados como union types
/// Permite pattern matching exhaustivo en la UI
@freezed
class AuthFailure with _$AuthFailure {
  /// Credenciales incorrectas
  const factory AuthFailure.invalidCredentials() = _InvalidCredentials;

  /// Email ya registrado
  const factory AuthFailure.emailAlreadyExists() = _EmailAlreadyExists;

  /// Sin conexión a internet
  const factory AuthFailure.noConnection() = _NoConnection;

  /// Sesión expirada — refrescar token
  const factory AuthFailure.sessionExpired() = _SessionExpired;

  /// Error del servidor (500, etc.)
  const factory AuthFailure.serverError({String? message}) = _ServerError;

  /// Error desconocido
  const factory AuthFailure.unknown({String? message}) = _Unknown;
}

extension AuthFailureX on AuthFailure {
  /// Mensaje legible para mostrar en UI
  String get userMessage => when(
    invalidCredentials: () => 'Email o contraseña incorrectos',
    emailAlreadyExists: () => 'Este email ya está registrado',
    noConnection: () => 'Sin conexión. Verifica tu internet',
    sessionExpired: () => 'Tu sesión expiró. Vuelve a iniciar sesión',
    serverError: (msg) => msg ?? 'Error del servidor. Intenta de nuevo',
    unknown: (msg) => msg ?? 'Ocurrió un error inesperado',
  );
}
