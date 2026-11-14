import 'package:freezed_annotation/freezed_annotation.dart';
import '../../domain/entities/auth_user.dart';
import '../../domain/failures/auth_failure.dart';

part 'auth_state.freezed.dart';

@freezed
class AuthState with _$AuthState {
  const AuthState._();

  const factory AuthState.loading() = _Loading;

  const factory AuthState.unauthenticated({
    AuthFailure? failure,
    @Default(false) bool isLoading,
  }) = _Unauthenticated;

  const factory AuthState.modeSelection({
    required AuthUser user,
    @Default(false) bool isLoading,
  }) = _ModeSelection;

  const factory AuthState.owner({required AuthUser user}) = _Owner;
  const factory AuthState.barber({required AuthUser user}) = _Barber;
  const factory AuthState.client({required AuthUser user}) = _Client;

  // ─── Computed ─────────────────────────────────────────────────────────────

  AuthUser? get user => maybeWhen(
    modeSelection: (user, _) => user,
    owner:   (user) => user,
    barber:  (user) => user,
    client:  (user) => user,
    orElse:  () => null,
  );

  AuthMode get mode => when(
    loading:         () => AuthMode.loading,
    unauthenticated: (_, __) => AuthMode.unauthenticated,
    modeSelection:   (_, __) => AuthMode.modeSelection,
    owner:           (_) => AuthMode.owner,
    barber:          (_) => AuthMode.barber,
    client:          (_) => AuthMode.client,
  );

  bool get isLoading => maybeWhen(
    unauthenticated: (_, isLoading) => isLoading,
    modeSelection:   (_, isLoading) => isLoading,
    orElse: () => false,
  );

  AuthFailure? get failure => maybeWhen(
    unauthenticated: (failure, _) => failure,
    orElse: () => null,
  );
}
