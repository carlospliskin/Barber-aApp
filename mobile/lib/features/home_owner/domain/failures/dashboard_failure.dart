import 'package:freezed_annotation/freezed_annotation.dart';

part 'dashboard_failure.freezed.dart';

@freezed
class DashboardFailure with _$DashboardFailure {
  const factory DashboardFailure.noConnection()              = _NoConnection;
  const factory DashboardFailure.unauthorized()              = _Unauthorized;
  const factory DashboardFailure.serverError({String? msg})  = _ServerError;
  const factory DashboardFailure.notFound()                  = _NotFound;
  const factory DashboardFailure.unknown({String? msg})      = _Unknown;
}

extension DashboardFailureX on DashboardFailure {
  String get userMessage => when(
    noConnection: () => 'Sin conexión. Verifica tu internet',
    unauthorized: () => 'Sesión expirada. Vuelve a ingresar',
    serverError:  (m) => m ?? 'Error del servidor',
    notFound:     () => 'Recurso no encontrado',
    unknown:      (m) => m ?? 'Error inesperado',
  );
}
