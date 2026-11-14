import 'package:freezed_annotation/freezed_annotation.dart';

part 'auth_failure.freezed.dart';

@freezed
class AuthFailure with _$AuthFailure {
  const factory AuthFailure.invalidCredentials()               = InvalidCredentials;
  const factory AuthFailure.emailAlreadyExists()               = EmailAlreadyExists;
  const factory AuthFailure.noConnection()                     = NoConnection;
  const factory AuthFailure.sessionExpired()                   = SessionExpired;
  const factory AuthFailure.serverError({String? message})     = ServerError;
  const factory AuthFailure.unknown({String? message})         = Unknown;
}

extension AuthFailureX on AuthFailure {
  String get userMessage => when(
    invalidCredentials: () => 'Email o contraseña incorrectos',
    emailAlreadyExists: () => 'Este email ya está registrado',
    noConnection:       () => 'Sin conexión. Verifica tu internet',
    sessionExpired:     () => 'Tu sesión expiró. Vuelve a iniciar sesión',
    serverError: (msg)  => msg ?? 'Error del servidor. Intenta de nuevo',
    unknown:     (msg)  => msg ?? 'Ocurrió un error inesperado',
  );

  bool get isRetryable => when(
    invalidCredentials: () => false,
    emailAlreadyExists: () => false,
    noConnection:       () => true,
    sessionExpired:     () => false,
    serverError:        (_) => true,
    unknown:            (_) => true,
  );
}
