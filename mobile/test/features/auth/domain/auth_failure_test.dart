import 'package:flutter_test/flutter_test.dart';
import 'package:barberia_app/features/auth/domain/failures/auth_failure.dart';

void main() {
  group('AuthFailure', () {

    group('userMessage', () {
      test('invalidCredentials retorna mensaje correcto', () {
        const f = AuthFailure.invalidCredentials();
        expect(f.userMessage, 'Email o contraseña incorrectos');
      });

      test('emailAlreadyExists retorna mensaje correcto', () {
        const f = AuthFailure.emailAlreadyExists();
        expect(f.userMessage, 'Este email ya está registrado');
      });

      test('noConnection retorna mensaje correcto', () {
        const f = AuthFailure.noConnection();
        expect(f.userMessage, 'Sin conexión. Verifica tu internet');
      });

      test('sessionExpired retorna mensaje correcto', () {
        const f = AuthFailure.sessionExpired();
        expect(f.userMessage, 'Tu sesión expiró. Vuelve a iniciar sesión');
      });

      test('serverError sin mensaje retorna fallback', () {
        const f = AuthFailure.serverError();
        expect(f.userMessage, 'Error del servidor. Intenta de nuevo');
      });

      test('serverError con mensaje lo retorna', () {
        const f = AuthFailure.serverError(message: 'Internal Server Error');
        expect(f.userMessage, 'Internal Server Error');
      });

      test('unknown sin mensaje retorna fallback', () {
        const f = AuthFailure.unknown();
        expect(f.userMessage, 'Ocurrió un error inesperado');
      });

      test('unknown con mensaje lo retorna', () {
        const f = AuthFailure.unknown(message: 'Algo raro pasó');
        expect(f.userMessage, 'Algo raro pasó');
      });
    });

    group('isRetryable', () {
      test('noConnection es retryable', () {
        expect(const AuthFailure.noConnection().isRetryable, isTrue);
      });

      test('serverError es retryable', () {
        expect(const AuthFailure.serverError().isRetryable, isTrue);
      });

      test('invalidCredentials NO es retryable', () {
        expect(const AuthFailure.invalidCredentials().isRetryable, isFalse);
      });

      test('emailAlreadyExists NO es retryable', () {
        expect(const AuthFailure.emailAlreadyExists().isRetryable, isFalse);
      });

      test('sessionExpired NO es retryable', () {
        expect(const AuthFailure.sessionExpired().isRetryable, isFalse);
      });
    });

    group('equality — Freezed', () {
      test('dos AuthFailure.noConnection() son iguales', () {
        expect(
          const AuthFailure.noConnection(),
          equals(const AuthFailure.noConnection()),
        );
      });

      test('failures distintos no son iguales', () {
        expect(
          const AuthFailure.noConnection(),
          isNot(equals(const AuthFailure.invalidCredentials())),
        );
      });

      test('serverError con mismo mensaje son iguales', () {
        expect(
          const AuthFailure.serverError(message: 'test'),
          equals(const AuthFailure.serverError(message: 'test')),
        );
      });

      test('serverError con distinto mensaje no son iguales', () {
        expect(
          const AuthFailure.serverError(message: 'a'),
          isNot(equals(const AuthFailure.serverError(message: 'b'))),
        );
      });
    });
  });
}
