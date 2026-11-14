import 'package:freezed_annotation/freezed_annotation.dart';

part 'client_failure.freezed.dart';

@freezed
class ClientFailure with _$ClientFailure {
  const factory ClientFailure.noConnection()                    = _NoConnection;
  const factory ClientFailure.unauthorized()                    = _Unauthorized;
  const factory ClientFailure.notFound()                        = _NotFound;
  const factory ClientFailure.serverError({String? message})    = _ServerError;
  const factory ClientFailure.noBarberias()                     = _NoBarberias;
  const factory ClientFailure.noSlots()                         = _NoSlots;
  const factory ClientFailure.cancelNotAllowed({String? reason})= _CancelNotAllowed;
  const factory ClientFailure.penalized({String? until})        = _Penalized;
  const factory ClientFailure.unknown({String? message})        = _Unknown;
}

extension ClientFailureX on ClientFailure {
  String get userMessage => when(
    noConnection:     () => 'Sin conexión. Verifica tu internet',
    unauthorized:     () => 'Sesión expirada. Vuelve a ingresar',
    notFound:         () => 'No encontrado',
    serverError:      (m) => m ?? 'Error del servidor',
    noBarberias:      () => 'No hay barberías disponibles',
    noSlots:          () => 'No hay horarios disponibles para esa fecha',
    cancelNotAllowed: (r) => r ?? 'No se puede cancelar esta cita',
    penalized:        (u) => u != null
        ? 'Cuenta penalizada hasta $u'
        : 'Tu cuenta tiene una penalización activa',
    unknown:          (m) => m ?? 'Error inesperado',
  );
}
