import { request } from './http.js'

export const listVenues = () => request('/catalog/venues').then((res) => res?.venues || [])

export const listPrograms = () => request('/catalog/programs').then((res) => res?.programs || [])

export const listPlans = () => request('/catalog/plans').then((res) => res?.plans || [])

export const getAvailability = ({ venueCode, programNo, date }) => {
  const query = new URLSearchParams({ venue: venueCode, program: programNo, date })
  return request(`/availability?${query.toString()}`)
}
