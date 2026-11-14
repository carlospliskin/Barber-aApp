import 'package:freezed_annotation/freezed_annotation.dart';
import '../../domain/entities/dashboard_entities.dart';
import '../../domain/failures/dashboard_failure.dart';

part 'dashboard_state.freezed.dart';

@freezed
class DashboardState with _$DashboardState {
  const DashboardState._();

  const factory DashboardState.loading() = _Loading;

  const factory DashboardState.loaded({
    required List<Appointment> appointments,
    required DashboardMetrics metrics,
    required DateTime selectedDate,
    @Default(false) bool isMarkingNoShow,
  }) = _Loaded;

  const factory DashboardState.error({
    required DashboardFailure failure,
    required DateTime selectedDate,
  }) = _Error;

  // ─── Computed ─────────────────────────────────────────────────────────────

  bool get isLoading => this is _Loading;

  List<Appointment> get appointments => maybeWhen(
    loaded: (appointments, _, __, ___) => appointments,
    orElse: () => [],
  );

  DashboardMetrics? get metrics => maybeWhen(
    loaded: (_, metrics, __, ___) => metrics,
    orElse: () => null,
  );

  DateTime get selectedDate => maybeWhen(
    loaded: (_, __, date, ___) => date,
    error:  (_, date) => date,
    orElse: () => DateTime.now(),
  );

  DashboardFailure? get failure => maybeWhen(
    error: (f, _) => f,
    orElse: () => null,
  );
}
